package com.vivanetpay.integration.android;

import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Set;

/**
 * Validación ESTRICTA del cuerpo (Anexo B). Reglas:
 * <ul>
 *   <li>Objeto JSON UTF-8, sin claves duplicadas, profundidad máxima 2, sin campos desconocidos.</li>
 *   <li>{@code schemaVersion} (entero) = 1. Obligatorio.</li>
 *   <li>{@code eventId}: texto 1..128, sin caracteres de control. Obligatorio.</li>
 *   <li>{@code source}: opcional; si viene debe ser {@code ANDROID_NEQUI}.</li>
 *   <li>{@code title}: texto 0..256. Obligatorio (puede ser vacío).</li>
 *   <li>{@code text}: texto 1..4096 no vacío. Obligatorio.</li>
 *   <li>{@code capturedAt}: ISO-8601 con zona (p. ej. {@code 2026-11-22T18:30:05Z}) o epoch en segundos/milisegundos
 *       (número o texto). Entre 2020-01-01 y ahora + 1 día. Obligatorio.</li>
 *   <li>{@code app} (0..128), {@code device} (0..64): opcionales. {@code test}: booleano opcional.</li>
 * </ul>
 * Los mensajes de error nombran el campo, nunca reproducen su valor.
 */
final class WebhookPayloadParser {
    static final int MAX_EVENT_ID = 128;
    static final int MAX_TITLE = 256;
    static final int MAX_TEXT = 4_096;
    static final int MAX_APP = 128;
    static final int MAX_DEVICE = 64;
    private static final Instant MIN_TIME = Instant.parse("2020-01-01T00:00:00Z");
    private static final Set<String> ALLOWED = Set.of("schemaVersion", "eventId", "source", "app", "title", "text",
            "capturedAt", "device", "test");

    /** Error de esquema; {@code code} es estable para el emisor, {@code detail} es seguro de mostrar. */
    static final class InvalidPayloadException extends Exception {
        final String code;

        InvalidPayloadException(String code, String detail) {
            super(detail, null, false, false);
            this.code = code;
        }
    }

    private final ObjectMapper mapper;

    WebhookPayloadParser() {
        JsonFactory factory = JsonFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxNestingDepth(2).maxStringLength(MAX_TEXT * 2).maxNumberLength(20).build())
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .disable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
                .build();
        // Jackson 3: el mapper es inmutable y se configura solo en el builder.
        this.mapper = JsonMapper.builder(factory)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .build();
    }

    WebhookPayload parse(byte[] body, Instant now) throws InvalidPayloadException {
        JsonNode root;
        try {
            root = mapper.readTree(body);
        } catch (JacksonException e) {
            throw new InvalidPayloadException("malformed_json", "El cuerpo no es JSON válido");
        }
        if (root == null || !root.isObject()) {
            throw new InvalidPayloadException("malformed_json", "Se esperaba un objeto JSON");
        }
        return parseObject(root, now);
    }

    /**
     * Formato alternativo ESTRICTO {@code application/x-www-form-urlencoded}: los mismos campos y reglas que el JSON
     * (sin campos desconocidos ni repetidos, UTF-8, secuencias %XX válidas). Existe porque el JSON armado por
     * MacroDroid se rompe si la notificación trae comillas o saltos de línea; con formulario el valor se codifica.
     */
    WebhookPayload parseForm(byte[] body, Instant now) throws InvalidPayloadException {
        String s;
        try {
            s = java.nio.charset.StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(body)).toString();
        } catch (java.nio.charset.CharacterCodingException e) {
            throw new InvalidPayloadException("malformed_form", "El cuerpo no es UTF-8 válido");
        }
        s = s.replaceAll("[\\r\\n]+$", ""); // un salto de línea final (copiar/pegar) no es parte del último valor
        ObjectNode root = mapper.createObjectNode();
        if (!s.isEmpty()) {
            for (String pair : s.split("&", -1)) {
                if (pair.isEmpty()) {
                    continue; // tolera '&' final
                }
                int eq = pair.indexOf('=');
                String rawKey = eq < 0 ? pair : pair.substring(0, eq);
                String rawVal = eq < 0 ? "" : pair.substring(eq + 1);
                String key = decode(rawKey);
                String val = decode(rawVal);
                if (!ALLOWED.contains(key)) {
                    throw new InvalidPayloadException("unknown_field", "Campo no permitido: " + safeName(key));
                }
                if (root.has(key)) {
                    throw new InvalidPayloadException("malformed_form", "Campo repetido: " + safeName(key));
                }
                if (key.equals("schemaVersion")) {
                    if (!val.strip().matches("\\d{1,9}")) {
                        throw new InvalidPayloadException("invalid_schema", "schemaVersion obligatorio (entero)");
                    }
                    root.put(key, Integer.parseInt(val.strip()));
                } else if (key.equals("test")) {
                    String v = val.strip().toLowerCase(java.util.Locale.ROOT);
                    if (!v.equals("true") && !v.equals("false")) {
                        throw new InvalidPayloadException("invalid_schema", "test debe ser booleano");
                    }
                    root.put(key, v.equals("true"));
                } else {
                    root.put(key, val);
                }
            }
        }
        return parseObject(root, now);
    }

    private static String decode(String s) throws InvalidPayloadException {
        try {
            return java.net.URLDecoder.decode(s, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new InvalidPayloadException("malformed_form", "Codificación %XX inválida");
        }
    }

    private WebhookPayload parseObject(JsonNode root, Instant now) throws InvalidPayloadException {
        for (String f : root.propertyNames()) {
            if (!ALLOWED.contains(f)) {
                throw new InvalidPayloadException("unknown_field", "Campo no permitido: " + safeName(f));
            }
        }
        JsonNode sv = root.get("schemaVersion");
        if (sv == null || !sv.canConvertToInt() || !sv.isIntegralNumber()) {
            throw new InvalidPayloadException("invalid_schema", "schemaVersion obligatorio (entero)");
        }
        if (sv.intValue() != 1) {
            throw new InvalidPayloadException("unsupported_schema_version", "schemaVersion no soportado");
        }
        String eventId = text(root, "eventId", true, 1, MAX_EVENT_ID, false);
        String source = text(root, "source", false, 0, 32, false);
        if (source != null && !source.equals("ANDROID_NEQUI")) {
            throw new InvalidPayloadException("invalid_schema", "source debe ser ANDROID_NEQUI");
        }
        String title = text(root, "title", true, 0, MAX_TITLE, true);
        String text = text(root, "text", true, 1, MAX_TEXT, true);
        if (text.isBlank()) {
            throw new InvalidPayloadException("invalid_schema", "text no puede estar vacío");
        }
        String app = text(root, "app", false, 0, MAX_APP, false);
        String device = text(root, "device", false, 0, MAX_DEVICE, false);
        boolean test = false;
        JsonNode t = root.get("test");
        if (t != null && !t.isNull()) {
            if (!t.isBoolean()) {
                throw new InvalidPayloadException("invalid_schema", "test debe ser booleano");
            }
            test = t.booleanValue();
        }
        Instant capturedAt = instant(root.get("capturedAt"), now);
        return new WebhookPayload(1, eventId.strip(), "ANDROID_NEQUI", blankToNull(app), title, text, capturedAt,
                blankToNull(device), test);
    }

    private static String text(JsonNode root, String field, boolean required, int min, int max, boolean multiline)
            throws InvalidPayloadException {
        JsonNode n = root.get(field);
        if (n == null || n.isNull()) {
            if (required) {
                throw new InvalidPayloadException("invalid_schema", field + " es obligatorio");
            }
            return null;
        }
        if (!n.isString()) {
            throw new InvalidPayloadException("invalid_schema", field + " debe ser texto");
        }
        String v = n.stringValue();
        int len = v.codePointCount(0, v.length());
        if (len < min || len > max) {
            throw new InvalidPayloadException("invalid_schema", field + " debe tener entre " + min + " y " + max + " caracteres");
        }
        if (min > 0 && v.isBlank()) {
            throw new InvalidPayloadException("invalid_schema", field + " no puede estar vacío");
        }
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            boolean allowedWs = multiline && (c == '\n' || c == '\r' || c == '\t');
            if ((Character.isISOControl(c) && !allowedWs) || c == ' ' || c == ' ') {
                throw new InvalidPayloadException("invalid_schema", field + " contiene caracteres de control");
            }
        }
        return v;
    }

    private static Instant instant(JsonNode n, Instant now) throws InvalidPayloadException {
        if (n == null || n.isNull()) {
            throw new InvalidPayloadException("invalid_schema", "capturedAt es obligatorio");
        }
        Instant value;
        if (n.isIntegralNumber()) {
            value = fromEpoch(n.asLong());
        } else if (n.isString()) {
            String s = n.stringValue().strip();
            if (s.length() > 40) {
                throw new InvalidPayloadException("invalid_schema", "capturedAt demasiado largo");
            }
            if (s.matches("\\d{9,13}")) {
                value = fromEpoch(Long.parseLong(s));
            } else {
                try {
                    value = OffsetDateTime.parse(s).toInstant();
                } catch (DateTimeParseException e) {
                    throw new InvalidPayloadException("invalid_schema",
                            "capturedAt debe ser ISO-8601 con zona o epoch (segundos/milisegundos)");
                }
            }
        } else {
            throw new InvalidPayloadException("invalid_schema", "capturedAt debe ser texto o número");
        }
        if (value.isBefore(MIN_TIME) || value.isAfter(now.plusSeconds(86_400))) {
            throw new InvalidPayloadException("invalid_schema", "capturedAt fuera de rango (revise la hora del teléfono)");
        }
        return value;
    }

    private static Instant fromEpoch(long v) {
        return v >= 100_000_000_000L ? Instant.ofEpochMilli(v) : Instant.ofEpochSecond(v);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static String safeName(String f) {
        String s = f.replaceAll("[^A-Za-z0-9_]", "?");
        return s.length() > 40 ? s.substring(0, 40) + "..." : s;
    }
}
