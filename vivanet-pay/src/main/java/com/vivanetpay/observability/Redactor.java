package com.vivanetpay.observability;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enmascara secretos y datos personales antes de escribir logs o diagnósticos (RF-018, RNF-012, IDD §8.1).
 * <ul>
 *   <li>Cabeceras {@code Authorization: Bearer ...} y {@code Basic ...}.</li>
 *   <li>Pares clave/valor sensibles: token, access_token, refresh_token, client_secret, password, secret,
 *       api_key, signature, nonce... en formato {@code k=v}, {@code k: v} o JSON.</li>
 *   <li>Tokens Google ({@code ya29.}, {@code 1//}), JWT ({@code eyJ...}) y cadenas tipo clave de 40+ caracteres.</li>
 *   <li>Correos: {@code juan.perez@gmail.com} -> {@code j***@gmail.com}.</li>
 *   <li>Secuencias de 8+ dígitos (teléfonos, cuentas, documentos): se conservan los 4 últimos.</li>
 * </ul>
 * Es conservador: puede enmascarar de más, nunca debería dejar pasar un token.
 */
public final class Redactor {
    public static final String MASK = "***";

    private static final Pattern AUTH_HEADER = Pattern.compile("(?i)\\b(bearer|basic)\\s+[A-Za-z0-9._~+/=-]+");
    /** {@code Authorization: <esquema> <credencial>} con cualquier esquema (Token, Digest, ApiKey...). */
    /** Esquema Digest: sus parámetros (response, nonce...) ocupan el resto de la línea. */
    private static final Pattern DIGEST = Pattern.compile("(?i)(\\bdigest\\s+)[^\\r\\n]+");
    private static final Pattern AUTH_ANY_SCHEME = Pattern.compile(
            "(?i)(\\bauthorization\\s*[:=]\\s*[\"']?)[A-Za-z][A-Za-z-]*\\s+[^\\s\"',;]+");
    /**
     * Pares clave/valor sensibles. Las claves "fuertes" se reconocen aunque lleven prefijo o sufijo
     * ({@code clientSecret}, {@code refreshToken}, {@code x-vivanet-signature}, {@code webhook_secret_previous});
     * las "débiles" ({@code code}, {@code nonce}, {@code pin}, {@code otp}) solo como palabra completa.
     * El valor puede ir entre comillas dobles o simples (con espacios y escapes) o sin comillas.
     */
    private static final Pattern SENSITIVE_KV = Pattern.compile(
            "(?i)((?:(?<![A-Za-z0-9_])(?:code|nonce|pin|otp)|[A-Za-z0-9_.-]{0,40}(?:token|secret|password|passwd|pwd|passphrase"
                    + "|contrase[\u00f1n]a|api[_-]?key|apikey|authorization|signature|credential|cookie)[A-Za-z0-9_.-]{0,40})"
                    + "[\"']?\\s*[:=]\\s*)"
                    + "(?:\"((?:[^\"\\\\]|\\\\.)*)\"|'((?:[^'\\\\]|\\\\.)*)'|([^\"'\\s,;&}]+))");
    private static final Pattern GOOGLE_TOKEN = Pattern.compile("\\b(?:ya29\\.[A-Za-z0-9._-]+|1//[A-Za-z0-9._-]+)");
    private static final Pattern JWT = Pattern.compile("\\beyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]*");
    private static final Pattern LONG_KEY = Pattern.compile("\\b[A-Za-z0-9_+/=-]{40,}\\b");
    private static final Pattern EMAIL = Pattern.compile("([A-Za-z0-9._%+-])[A-Za-z0-9._%+-]*@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})");
    private static final Pattern LONG_NUMBER = Pattern.compile("(?<![\\dA-Za-z.\\-])\\d{4,}(\\d{4})(?![\\dA-Za-z\\-])");
    /** Teléfonos con separadores: {@code +57 300 123 4567}, {@code 300-123-4567}. */
    private static final Pattern PHONE = Pattern.compile(
            "(?<![\\dA-Za-z.\\-])(?:\\+?\\d{1,3}[ -])?\\d{3}[ -]\\d{3}[ -](\\d{4})(?![\\dA-Za-z\\-])");


    private Redactor() {
    }

    /** Devuelve el texto con los datos sensibles enmascarados. Acepta null. */
    public static String redact(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String s = DIGEST.matcher(input).replaceAll("$1" + MASK);
        s = AUTH_HEADER.matcher(s).replaceAll("$1 " + MASK);
        s = AUTH_ANY_SCHEME.matcher(s).replaceAll("$1" + MASK);
        s = SENSITIVE_KV.matcher(s).replaceAll(m -> {
            String q = m.group(2) != null ? "\"" : m.group(3) != null ? "'" : "";
            return Matcher.quoteReplacement(m.group(1) + q + MASK + q);
        });
        s = GOOGLE_TOKEN.matcher(s).replaceAll(MASK);
        s = JWT.matcher(s).replaceAll(MASK);
        s = LONG_KEY.matcher(s).replaceAll(MASK);
        s = EMAIL.matcher(s).replaceAll("$1***@$2");
        s = PHONE.matcher(s).replaceAll(m -> MASK + m.group(1));
        s = LONG_NUMBER.matcher(s).replaceAll(m -> MASK + m.group(1));
        return s;
    }

}
