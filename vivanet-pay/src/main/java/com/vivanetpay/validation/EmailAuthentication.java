package com.vivanetpay.validation;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Evaluación de las cabeceras {@code Authentication-Results} (o, si faltan, {@code ARC-Authentication-Results}) de un
 * correo para el dominio esperado del banco. Pura y sin E/S.
 * <p>
 * Solo se confían las instancias de la cabecera emitidas por un servidor de autenticación de Google
 * ({@code mx.google.com}): el remitente podría incluir su propia cabecera falsa, pero no puede fijar el
 * identificador del servidor que Gmail antepone. Resultado positivo: {@code dkim=pass} alineado con el dominio
 * esperado Y ({@code spf=pass} o {@code dmarc=pass}) alineados; cualquier {@code dmarc=fail} lo anula.
 */
public final class EmailAuthentication {
    private static final Pattern RESULT = Pattern.compile("^\\s*(dkim|spf|dmarc)\\s*=\\s*([a-z]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PROP = Pattern.compile(
            "(header\\.d|header\\.i|header\\.from|smtp\\.mailfrom|smtp\\.helo)\\s*=\\s*\"?([^\\s;)\"]+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ARC_PREFIX = Pattern.compile("^\\s*i\\s*=\\s*\\d+\\s*;");

    /** @param passed true si se cumple dkim=pass y (spf=pass o dmarc=pass) para el dominio esperado. */
    public record Result(boolean passed, String detail) {
    }

    private EmailAuthentication() {
    }

    public static Result evaluate(Map<String, String> headers, Set<String> expectedDomains) {
        String ar = header(headers, "Authentication-Results");
        String source = "Authentication-Results";
        if (ar == null || ar.isBlank()) {
            ar = header(headers, "ARC-Authentication-Results");
            source = "ARC-Authentication-Results";
        }
        if (ar == null || ar.isBlank()) {
            return new Result(false, "el correo no trae cabecera de autenticación");
        }
        boolean dkim = false;
        boolean spf = false;
        boolean dmarc = false;
        boolean dmarcFail = false;
        boolean trustedInstance = false;
        for (String instance : ar.split("\\R(?=\\s*(?:i\\s*=\\s*\\d+\\s*;\\s*)?[A-Za-z0-9.-]+\\s*;)")) {
            String body = ARC_PREFIX.matcher(instance).replaceFirst("").strip();
            String[] segments = body.split(";");
            if (segments.length < 2 || !trustedServer(segments[0])) {
                continue;
            }
            trustedInstance = true;
            for (int i = 1; i < segments.length; i++) {
                Matcher m = RESULT.matcher(segments[i]);
                if (!m.find()) {
                    continue;
                }
                String method = m.group(1).toLowerCase(Locale.ROOT);
                boolean pass = m.group(2).equalsIgnoreCase("pass");
                boolean aligned = aligned(segments[i], method, expectedDomains);
                switch (method) {
                    case "dkim" -> dkim |= pass && aligned;
                    case "spf" -> spf |= pass && aligned;
                    default -> {
                        dmarc |= pass && aligned;
                        dmarcFail |= m.group(2).equalsIgnoreCase("fail");
                    }
                }
            }
        }
        if (!trustedInstance) {
            return new Result(false, "la cabecera de autenticación no proviene del servidor de correo de Google");
        }
        if (dmarcFail) {
            return new Result(false, "DMARC falló");
        }
        if (!dkim) {
            return new Result(false, "DKIM no pasó para el dominio esperado");
        }
        if (!spf && !dmarc) {
            return new Result(false, "ni SPF ni DMARC pasaron para el dominio esperado");
        }
        return new Result(true, "dkim=pass y " + (dmarc ? "dmarc=pass" : "spf=pass") + " (" + source + ")");
    }

    private static boolean trustedServer(String authservSegment) {
        String id = authservSegment.strip().split("\\s+")[0].toLowerCase(Locale.ROOT);
        return id.equals("mx.google.com") || id.endsWith(".mx.google.com") || id.equals("google.com");
    }

    private static boolean aligned(String segment, String method, Set<String> expected) {
        Matcher m = PROP.matcher(segment);
        while (m.find()) {
            String key = m.group(1).toLowerCase(Locale.ROOT);
            boolean relevant = switch (method) {
                case "dkim" -> key.equals("header.d") || key.equals("header.i");
                case "spf" -> key.equals("smtp.mailfrom") || key.equals("smtp.helo");
                default -> key.equals("header.from");
            };
            if (!relevant) {
                continue;
            }
            String v = m.group(2).toLowerCase(Locale.ROOT);
            int at = v.lastIndexOf('@');
            String domain = at >= 0 ? v.substring(at + 1) : v;
            if (domainMatches(domain, expected)) {
                return true;
            }
        }
        return false;
    }

    static boolean domainMatches(String domain, Set<String> expected) {
        for (String e : expected) {
            if (domain.equals(e) || domain.endsWith("." + e)) {
                return true;
            }
        }
        return false;
    }

    private static String header(Map<String, String> headers, String name) {
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (e.getKey().equalsIgnoreCase(name)) {
                return e.getValue();
            }
        }
        return null;
    }
}
