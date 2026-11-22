package com.vivanetpay.parsing;

import com.vivanetpay.domain.RawEvent;
import com.vivanetpay.validation.AmountNormalizer;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Base de los parsers por plantillas (Nequi push, Nequi correo, Bancolombia correo).
 * <p>
 * Las plantillas se evalúan sobre una versión "plegada" del texto (minúsculas, sin tildes ni eñes, espacios no
 * separables convertidos en espacios) que conserva la longitud original, de modo que los índices sirven para
 * recuperar nombres con su capitalización y tildes originales.
 * <p>
 * Principios: el texto no es evidencia suficiente (IDD 8.2): un importe ambiguo, una fuente no verificada o varios
 * importes bajan la confianza; el parser nunca confirma nada. Cero/negativo se devuelve como importe no positivo
 * para que el validador lo rechace (CP-010); malformado se devuelve como no reconocido.
 */
abstract class AbstractTemplateParser implements VersionedParser {

    static final double CONF_TEMPLATE = 0.98;
    static final double CONF_NO_SENDER = 0.95;
    static final double CONF_LOW = 0.5;

    /** Importe: signo opcional, símbolo $ y/o COP opcionales, dígitos con separadores. Grupo {@code amt}. */
    static final String AMT = "(?<amt>-?\\s{0,2}(?:cop\\s{0,2})?-?\\$?\\s{0,2}(?:cop\\s{0,2})?\\d(?:[\\d.,]*\\d)?)";
    static final String NAME_CHARS = "[a-z][a-z '’-]{1,60}?";
    /** Nombre antes del verbo: tras inicio o un delimitador. Grupo {@code name}. */
    static final String NAME_BEFORE = "(?:^|[.!?:¡¿,;\\n]\\s{0,3})(?<name>" + NAME_CHARS + ")\\s+";
    /** Fin de un nombre que sigue a "de": palabra de cierre, puntuación o fin de texto. */
    static final String NAME_END = "(?=\\s+(?:en|por|con|ref|referencia|desde|a\\s+(?:tu|su))\\b|\\s*[.,!;:\\n]|\\s*$)";

    private static final Pattern REF = Pattern.compile(
            "(?:referencia|ref|comprobante|aprobacion)\\.?\\s{0,2}(?:no\\.?|nro\\.?|numero)?\\s{0,2}[:#]?\\s{0,2}(?<ref>[a-z0-9-]{4,30})");
    private static final Pattern ANY_AMOUNT = Pattern.compile("(?:\\$\\s{0,2}|cop\\s{0,2})(\\d(?:[\\d.,]*\\d)?)");
    private static final Pattern LINK = Pattern.compile("https?:|www\\.|bit\\.ly|\\.com/|\\.co/");

    /** Plantilla nombrada con su patrón sobre texto plegado. */
    record Template(String name, Pattern pattern) {
        static Template of(String name, String regex) {
            return new Template(name, Pattern.compile(regex));
        }
    }

    /** Motivo de descarte: patrón sobre texto plegado y descripción. */
    record Reject(Pattern pattern, String reason) {
        static Reject of(String regex, String reason) {
            return new Reject(Pattern.compile(regex), reason);
        }
    }

    /** Resultado de verificar el origen: tope de confianza y nota (null si verificado). */
    record Origin(double confidenceCap, String note) {
        static final Origin OK = new Origin(1.0, null);
    }

    protected abstract String bank();

    protected abstract List<Template> templates();

    protected abstract List<Reject> rejects();

    /** Verifica remitente/origen. Por defecto sin restricciones. */
    protected Origin verifyOrigin(RawEvent event) {
        return Origin.OK;
    }

    /** Si true, varios importes distintos en el texto bajan la confianza. */
    protected boolean checkMultipleAmounts() {
        return false;
    }

    /** Si true, enlaces en el texto bajan la confianza. */
    protected boolean checkLinks() {
        return false;
    }

    protected String templateTag(String templateName) {
        return id() + "/" + templateName;
    }

    @Override
    public ParseResult parse(RawEvent event) {
        String text = joinText(event);
        if (text.isBlank()) {
            return ParseResult.notRecognized(id(), "Texto vacío");
        }
        String folded = fold(text);
        for (Reject r : rejects()) {
            if (r.pattern().matcher(folded).find()) {
                return ParseResult.notRecognized(id(), "Mensaje descartado, no es un ingreso (" + r.reason() + ")");
            }
        }
        for (Template t : templates()) {
            Matcher m = t.pattern().matcher(folded);
            if (m.find()) {
                return build(event, text, folded, t, m);
            }
        }
        return ParseResult.notRecognized(id(), "Ninguna plantilla de " + id() + " coincide");
    }

    private ParseResult build(RawEvent event, String text, String folded, Template t, Matcher m) {
        String amtToken = text.substring(m.start("amt"), m.end("amt")).trim();
        double confidence = CONF_TEMPLATE;
        List<String> notes = new ArrayList<>();
        notes.add("plantilla " + templateTag(t.name()) + " v" + version() + " coincidió");

        boolean negative = amtToken.contains("-");
        String clean = amtToken.replace("-", "").trim();
        String numeric = clean.replaceAll("(?i)cop|\\$|\\s", "");
        Optional<Long> pesos = AmountNormalizer.parsePesos(clean);
        if (pesos.isEmpty()) {
            return ParseResult.notRecognized(id(), "Importe malformado: '" + amtToken + "' (plantilla "
                    + templateTag(t.name()) + ")");
        }
        long amount = negative ? -pesos.get() : pesos.get();
        if (amount <= 0) {
            notes.add("importe no positivo: no se contabiliza");
        } else if (isAmbiguous(numeric)) {
            confidence = Math.min(confidence, CONF_LOW);
            notes.add("formato de importe ambiguo ('" + numeric + "'): requiere revisión");
        }

        if (amount > 0 && continuesAfterGap(text, m.end("amt"))) {
            // "$5<ZWSP>0.000" o "$50 000": el número visible podría ser otro que el extraído (IDD 8.2)
            confidence = Math.min(confidence, CONF_LOW);
            notes.add("el importe parece continuar tras un espacio o carácter invisible: requiere revisión");
        }

        String sender = null;
        if (hasGroup(m, "name")) {
            String raw = text.substring(m.start("name"), m.end("name")).trim().replaceAll("\\s+", " ");
            if (raw.length() >= 2) {
                sender = raw;
            }
        }
        if (sender == null) {
            confidence = Math.min(confidence, CONF_NO_SENDER);
            notes.add("remitente no indicado");
        }

        if (checkMultipleAmounts() && distinctAmounts(folded) > 1) {
            confidence = Math.min(confidence, CONF_LOW);
            notes.add("varios importes distintos en el texto");
        }
        if (checkLinks() && LINK.matcher(folded).find()) {
            confidence = Math.min(confidence, CONF_LOW);
            notes.add("contiene enlaces: posible suplantación");
        }
        Origin origin = verifyOrigin(event);
        if (origin.note() != null) {
            confidence = Math.min(confidence, origin.confidenceCap());
            notes.add(origin.note());
        }

        String currency = folded.substring(m.start("amt"), m.end("amt")).contains("cop") ? "COP" : null;
        return ParseResult.builder(id())
                .amount(amount)
                .currency(currency)
                .bank(bank())
                .sender(sender)
                .reference(extractReference(text, folded))
                .confidence(confidence)
                .reason(String.join("; ", notes))
                .build();
    }

    private static final Pattern THOUSANDS_GROUP = Pattern.compile("\\d{3}(?![\\d]|[.,]\\d)");

    /**
     * true si tras el importe extraído hay (a lo sumo 2) separadores y luego más dígitos que podrían formar parte del
     * mismo número visible: cualquier carácter invisible de formato (ZWSP, ZWJ, BOM...) seguido de un dígito, o un
     * espacio seguido de un grupo de exactamente tres dígitos ("$50 000").
     */
    static boolean continuesAfterGap(String text, int end) {
        int i = end;
        boolean invisible = false;
        int gap = 0;
        while (i < text.length() && gap < 2) {
            char c = text.charAt(i);
            int type = Character.getType(c);
            if (type == Character.FORMAT) {
                invisible = true;
            } else if (!(Character.isWhitespace(c) || c == '\u00A0' || c == '\u202F')) {
                break;
            }
            i++;
            gap++;
        }
        if (gap == 0 || i >= text.length() || text.charAt(i) < '0' || text.charAt(i) > '9') {
            return false;
        }
        return invisible || THOUSANDS_GROUP.matcher(text).region(i, text.length()).lookingAt();
    }

    private static boolean hasGroup(Matcher m, String name) {
        try {
            return m.start(name) >= 0;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Importe en formato no inequívoco para COP: "50,000", "50,000.00", "50.00". */
    static boolean isAmbiguous(String numeric) {
        int lastComma = numeric.lastIndexOf(',');
        int lastDot = numeric.lastIndexOf('.');
        if (lastComma >= 0) {
            String after = numeric.substring(lastComma + 1);
            return after.length() == 3 || lastDot > lastComma;
        }
        if (lastDot >= 0) {
            int digitsAfter = numeric.length() - lastDot - 1;
            return digitsAfter < 3;
        }
        return false;
    }

    private static int distinctAmounts(String folded) {
        Set<Long> values = new HashSet<>();
        Matcher m = ANY_AMOUNT.matcher(folded);
        while (m.find()) {
            Optional<Long> v = AmountNormalizer.parsePesos(m.group(1));
            values.add(v.orElse(-1L));
        }
        return values.size();
    }

    private static String extractReference(String text, String folded) {
        Matcher m = REF.matcher(folded);
        while (m.find()) {
            String candidate = text.substring(m.start("ref"), m.end("ref"));
            if (candidate.chars().anyMatch(Character::isDigit)) {
                return candidate;
            }
        }
        return null;
    }

    static String joinText(RawEvent event) {
        StringBuilder sb = new StringBuilder();
        if (event.subject() != null && !event.subject().isBlank()) {
            sb.append(event.subject()).append('\n');
        }
        sb.append(event.rawText());
        return sb.toString();
    }

    /** Minúsculas y sin tildes/eñes, 1 a 1 por carácter (conserva índices). */
    static String fold(String s) {
        char[] out = new char[s.length()];
        for (int i = 0; i < out.length; i++) {
            char c = s.charAt(i);
            if (c == ' ' || c == ' ' || c == ' ' || c == '​' || c == '﻿' || c == '\t'
                    || c == '\r') {
                c = ' ';
            }
            c = Character.toLowerCase(c);
            if (c > 127) {
                String d = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD);
                c = d.charAt(0);
            }
            out[i] = c;
        }
        return new String(out);
    }
}
