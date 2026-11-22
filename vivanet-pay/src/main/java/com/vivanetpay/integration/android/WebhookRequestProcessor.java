package com.vivanetpay.integration.android;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivanetpay.application.EventSink;
import com.vivanetpay.application.IngestionResult;
import com.vivanetpay.domain.EventSource;
import com.vivanetpay.domain.RawEvent;
import com.vivanetpay.observability.Correlation;
import com.vivanetpay.security.SecretBytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Lógica del webhook independiente del transporte (Flujo C pasos 20-23, CP-007..CP-009).
 * <p>
 * PUNTO DE EXTENSIÓN (DP-03/DP-04 pendientes): el receptor HTTP local ({@link AndroidWebhookConnector}) solo adapta
 * {@code HttpExchange} a {@link RequestMeta}/{@link BodySource}. Un futuro puente con servicio intermedio en la nube
 * (p. ej. un conector que descargue por HTTPS saliente los eventos encolados en el relay) debe reutilizar este
 * procesador tal cual, de modo que autenticación, anti-replay, esquema e idempotencia sean idénticos.
 * <p>
 * Orden de verificación: ruta → tasa por IP (429) → método (405) → pausa (503) → Content-Type (415) →
 * tamaño (413) → autenticación (401) → nonce (409) → esquema (400) → prueba (200 sin núcleo) → ingesta (202/200).
 * Los rechazos se auditan en log con motivo, IP, método y ruta; NUNCA cuerpo, firma, token ni nonce.
 */
public final class WebhookRequestProcessor {
    private static final Logger log = LoggerFactory.getLogger(WebhookRequestProcessor.class);
    private static final Pattern NONCE = Pattern.compile("[A-Za-z0-9_-]{16,128}");
    private static final Pattern TIMESTAMP = Pattern.compile("\\d{1,12}");
    private static final Pattern HEX64 = Pattern.compile("[0-9a-fA-F]{64}");
    private static final int AUTH_FAILURE_PENALTY = 4;

    /** Metadatos de la solicitud. {@code headers} devuelve todos los valores de una cabecera (insensible a mayúsculas). */
    public record RequestMeta(String method, String path, String remoteIp, Function<String, List<String>> headers) {
        String single(String name) {
            List<String> v = headers.apply(name);
            return v == null || v.size() != 1 ? null : v.get(0);
        }
    }

    /** Lectura del cuerpo con tope real. */
    @FunctionalInterface
    public interface BodySource {
        /** @return el cuerpo, o null si supera {@code maxBytes} (no se lee más allá de maxBytes + 1). */
        byte[] read(int maxBytes) throws IOException;
    }

    /** Respuesta a enviar. */
    public record Response(int status, String json, Map<String, String> headers) {
    }

    private final String connectorId;
    private final AndroidWebhookConfig config;
    private final WebhookSecretManager secrets;
    private final EventSink sink;
    private final Clock clock;
    private final BooleanSupplier paused;
    private final Runnable onAccepted;
    private final NonceCache nonces;
    private final RateLimiter rateLimiter;
    private final WebhookPayloadParser parser = new WebhookPayloadParser();
    private final ObjectMapper json = new ObjectMapper();
    private volatile ExecutorService ingestExecutor;

    private final AtomicLong accepted = new AtomicLong();
    private final AtomicLong duplicates = new AtomicLong();
    private final AtomicLong tests = new AtomicLong();
    private final Map<String, AtomicLong> rejected = new ConcurrentHashMap<>();
    private final AtomicReference<Instant> lastAccepted = new AtomicReference<>();
    private final AtomicReference<Instant> lastTest = new AtomicReference<>();
    private final AtomicReference<Instant> lastRejected = new AtomicReference<>();

    public WebhookRequestProcessor(String connectorId, AndroidWebhookConfig config, WebhookSecretManager secrets,
                                   EventSink sink, Clock clock, BooleanSupplier paused, Runnable onAccepted) {
        this.connectorId = Objects.requireNonNull(connectorId);
        this.config = Objects.requireNonNull(config);
        this.secrets = Objects.requireNonNull(secrets);
        this.sink = Objects.requireNonNull(sink);
        this.clock = Objects.requireNonNull(clock);
        this.paused = Objects.requireNonNull(paused);
        this.onAccepted = Objects.requireNonNull(onAccepted);
        this.nonces = new NonceCache(config.nonceCacheSize(), config.maxSkew().multipliedBy(2));
        this.rateLimiter = new RateLimiter(config.rateBurst(), config.ratePerMinute(), System::nanoTime);
    }

    /** Ejecutor donde se llama a {@link EventSink#ingest} (fuera del hilo del servidor HTTP). */
    public void setIngestExecutor(ExecutorService executor) {
        this.ingestExecutor = executor;
    }

    public Response process(RequestMeta req, BodySource body) {
        String path = req.path() == null ? "" : req.path();
        boolean events = path.equals(AndroidWebhookConfig.EVENTS_PATH);
        boolean health = path.equals(AndroidWebhookConfig.HEALTH_PATH);
        if (!events && !health) {
            return reject(req, 404, "not_found", null);
        }
        if (!rateLimiter.tryAcquire(req.remoteIp())) {
            return reject(req, 429, "rate_limited", Map.of("Retry-After", Long.toString(rateLimiter.retryAfterSeconds())));
        }
        if (health) {
            if (!"GET".equals(req.method()) && !"HEAD".equals(req.method())) {
                return reject(req, 405, "method_not_allowed", Map.of("Allow", "GET, HEAD"));
            }
            return respond(200, ordered("status", paused.getAsBoolean() ? "paused" : "ok", "schemaVersion", 1), Map.of());
        }
        if (!"POST".equals(req.method())) {
            return reject(req, 405, "method_not_allowed", Map.of("Allow", "POST"));
        }
        if (paused.getAsBoolean()) {
            return reject(req, 503, "paused", Map.of("Retry-After", "60"));
        }
        if (!isJson(req.single("Content-Type"))) {
            return reject(req, 415, "unsupported_media_type", null);
        }
        String cl = req.single("Content-Length");
        if (cl != null) {
            try {
                if (Long.parseLong(cl.trim()) > config.maxBodyBytes()) {
                    return reject(req, 413, "payload_too_large", Map.of("Connection", "close"));
                }
            } catch (NumberFormatException e) {
                return reject(req, 400, "bad_request", null);
            }
        }
        byte[] raw;
        try {
            raw = body.read(config.maxBodyBytes());
        } catch (IOException e) {
            return reject(req, 400, "body_read_error", Map.of("Connection", "close"));
        }
        if (raw == null) {
            return reject(req, 413, "payload_too_large", Map.of("Connection", "close"));
        }

        String authFailure = config.authMode() == AuthMode.HMAC ? verifyHmac(req, raw) : verifyToken(req);
        if (authFailure != null) {
            rateLimiter.penalize(req.remoteIp(), AUTH_FAILURE_PENALTY);
            return reject(req, 401, authFailure, Map.of("WWW-Authenticate",
                    config.authMode() == AuthMode.HMAC ? "Vivanet-HMAC" : "Bearer"));
        }
        if (config.authMode() == AuthMode.HMAC) {
            switch (nonces.register(req.single(WebhookSigner.HEADER_NONCE), clock.instant())) {
                case REPLAY -> {
                    return reject(req, 409, "replay", null);
                }
                case FULL -> {
                    return reject(req, 503, "nonce_cache_full", Map.of("Retry-After", "30"));
                }
                default -> { }
            }
        }

        Instant now = clock.instant();
        WebhookPayload payload;
        try {
            payload = parser.parse(raw, now);
        } catch (WebhookPayloadParser.InvalidPayloadException e) {
            return reject(req, 400, e.code, null, e.getMessage());
        }
        if (payload.test()) {
            tests.incrementAndGet();
            lastTest.set(now);
            log.info("Webhook Android: evento de PRUEBA recibido desde {} (no se envía al núcleo).", req.remoteIp());
            return respond(200, ordered("status", "test_ok", "eventId", payload.eventId(),
                    "message", "Prueba recibida. No se registró ningún pago."), Map.of());
        }
        return ingest(req, payload, now);
    }

    private Response ingest(RequestMeta req, WebhookPayload p, Instant now) {
        RawEvent raw = toRawEvent(p, now);
        ExecutorService exec = ingestExecutor;
        if (exec == null) {
            return reject(req, 503, "not_running", Map.of("Retry-After", "30"));
        }
        try (Correlation ignored = Correlation.open(raw.correlationId())) {
            Future<IngestionResult> f;
            try {
                f = exec.submit(() -> {
                    try (Correlation c = Correlation.open(raw.correlationId())) {
                        return sink.ingest(raw);
                    }
                });
            } catch (RejectedExecutionException e) {
                return reject(req, 503, "busy", Map.of("Retry-After", "10"));
            }
            IngestionResult result;
            try {
                result = f.get(config.ingestTimeout().toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                log.warn("Webhook Android: el núcleo tarda en procesar el evento; se responde 202 (processing).");
                return respond(202, ordered("status", "processing", "eventId", p.eventId()), Map.of());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return respond(503, ordered("error", "interrupted"), Map.of("Retry-After", "10"));
            } catch (ExecutionException e) {
                log.error("Webhook Android: fallo al ingerir el evento", e.getCause());
                return respond(500, ordered("error", "internal_error"), Map.of());
            }
            if (result == null || !result.isPersisted()) {
                log.error("Webhook Android: el núcleo no pudo persistir el evento; el emisor debe reintentar.");
                return respond(500, ordered("error", "not_persisted"), Map.of());
            }
            if (result.isDuplicate()) {
                duplicates.incrementAndGet();
                log.info("Webhook Android: reenvío reconocido como duplicado desde {}.", req.remoteIp());
                return respond(200, ordered("status", "duplicate", "eventId", p.eventId()), Map.of());
            }
            accepted.incrementAndGet();
            lastAccepted.set(now);
            log.info("Webhook Android: evento aceptado desde {} (estado {}).", req.remoteIp(), result.status());
            try {
                onAccepted.run();
            } catch (RuntimeException e) {
                log.warn("Webhook Android: no se pudo actualizar el estado del conector", e);
            }
            return respond(202, ordered("status", "accepted", "eventId", p.eventId()), Map.of());
        }
    }

    /** Mapeo al contrato común (Anexo B). No interpreta importes. */
    RawEvent toRawEvent(WebhookPayload p, Instant receivedAt) {
        String title = p.title() == null ? "" : p.title().strip();
        String rawText = title.isEmpty() ? p.text() : title + "\n" + p.text();
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("webhook.auth", config.authMode().name().toLowerCase(Locale.ROOT));
        headers.put("webhook.schemaVersion", Integer.toString(p.schemaVersion()));
        if (p.app() != null) {
            headers.put("android.app", p.app());
        }
        if (p.device() != null) {
            headers.put("android.device", p.device());
        }
        return RawEvent.builder(EventSource.ANDROID_NEQUI, connectorId)
                .externalEventId(p.eventId())
                .sourceTimestamp(p.capturedAt())
                .receivedAt(receivedAt)
                .subject(title.isEmpty() ? null : title)
                .sender(p.app())
                .rawText(rawText)
                .headers(headers)
                .schemaVersion(RawEvent.CURRENT_SCHEMA_VERSION)
                .historical(false)
                .build();
    }

    private String verifyHmac(RequestMeta req, byte[] body) {
        String ts = req.single(WebhookSigner.HEADER_TIMESTAMP);
        String nonce = req.single(WebhookSigner.HEADER_NONCE);
        String sig = req.single(WebhookSigner.HEADER_SIGNATURE);
        if (ts == null || nonce == null || sig == null) {
            return "missing_auth_headers";
        }
        ts = ts.trim();
        if (!TIMESTAMP.matcher(ts).matches()) {
            return "invalid_timestamp";
        }
        long skew = Math.abs(clock.instant().getEpochSecond() - Long.parseLong(ts));
        if (skew > config.maxSkew().toSeconds()) {
            return "timestamp_out_of_window";
        }
        if (!NONCE.matcher(nonce).matches()) {
            return "invalid_nonce";
        }
        String hex = sig.trim();
        if (hex.startsWith(WebhookSigner.VERSION_PREFIX)) {
            hex = hex.substring(WebhookSigner.VERSION_PREFIX.length());
        }
        if (!HEX64.matcher(hex).matches()) {
            return "bad_signature";
        }
        byte[] provided = HexFormat.of().parseHex(hex.toLowerCase(Locale.ROOT));
        boolean ok = false;
        for (byte[] secret : secrets.acceptedSecrets()) {
            byte[] expected = WebhookSigner.mac(secret, ts, nonce, body);
            ok |= MessageDigest.isEqual(expected, provided); // tiempo constante; sin cortocircuito
            SecretBytes.wipe(secret);
        }
        return ok ? null : "bad_signature";
    }

    private String verifyToken(RequestMeta req) {
        String auth = req.single("Authorization");
        if (auth == null) {
            return "missing_token";
        }
        auth = auth.trim();
        if (auth.length() < 7 || !auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return "missing_token";
        }
        byte[] provided = auth.substring(7).trim().getBytes(StandardCharsets.UTF_8);
        boolean ok = false;
        for (byte[] secret : secrets.acceptedSecrets()) {
            ok |= MessageDigest.isEqual(secret, provided);
            SecretBytes.wipe(secret);
        }
        SecretBytes.wipe(provided);
        return ok ? null : "bad_token";
    }

    private static boolean isJson(String contentType) {
        if (contentType == null) {
            return false;
        }
        String[] parts = contentType.split(";");
        if (!parts[0].trim().equalsIgnoreCase("application/json")) {
            return false;
        }
        for (int i = 1; i < parts.length; i++) {
            String p = parts[i].trim().toLowerCase(Locale.ROOT);
            if (p.startsWith("charset=") && !p.substring(8).replace("\"", "").equals("utf-8")) {
                return false;
            }
        }
        return true;
    }

    private Response reject(RequestMeta req, int status, String reason, Map<String, String> headers) {
        return reject(req, status, reason, headers, null);
    }

    private Response reject(RequestMeta req, int status, String reason, Map<String, String> headers, String detail) {
        rejected.computeIfAbsent(reason, k -> new AtomicLong()).incrementAndGet();
        lastRejected.set(clock.instant());
        // Auditoría (CP-008): sin cuerpo, firma, token ni nonce.
        if (status == 429 || status == 404) {
            log.debug("Webhook Android rechazado: http={} motivo={} ip={} método={} ruta={}",
                    status, reason, req.remoteIp(), sanitize(req.method()), sanitize(req.path()));
        } else {
            log.warn("Webhook Android rechazado: http={} motivo={} ip={} método={} ruta={}",
                    status, reason, req.remoteIp(), sanitize(req.method()), sanitize(req.path()));
        }
        Map<String, Object> body = detail == null ? ordered("error", reason) : ordered("error", reason, "detail", detail);
        return respond(status, body, headers == null ? Map.of() : headers);
    }

    private Response respond(int status, Map<String, Object> body, Map<String, String> headers) {
        try {
            return new Response(status, json.writeValueAsString(body), headers);
        } catch (JsonProcessingException e) {
            return new Response(status, "{}", headers);
        }
    }

    private static Map<String, Object> ordered(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private static String sanitize(String s) {
        if (s == null) {
            return "";
        }
        String c = s.replaceAll("[^A-Za-z0-9/._-]", "?");
        return c.length() > 64 ? c.substring(0, 64) + "..." : c;
    }

    public WebhookStats stats() {
        Map<String, Long> r = new LinkedHashMap<>();
        rejected.forEach((k, v) -> r.put(k, v.get()));
        return new WebhookStats(accepted.get(), duplicates.get(), tests.get(), r, lastAccepted.get(),
                lastTest.get(), lastRejected.get());
    }

    Duration maxSkew() {
        return config.maxSkew();
    }
}
