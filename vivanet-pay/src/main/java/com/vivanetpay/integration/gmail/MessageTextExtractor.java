package com.vivanetpay.integration.gmail;

import com.vivanetpay.domain.RawEvent;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrae texto plano de un árbol MIME (text/plain preferido; si no, HTML convertido a texto simple) y
 * decodifica cabeceras RFC 2047. Sin librerías externas.
 */
public final class MessageTextExtractor {

    private static final Pattern CHARSET = Pattern.compile("(?i)charset\\s*=\\s*\"?([A-Za-z0-9_\\-:.]+)\"?");
    private static final Pattern ENCODED_WORD = Pattern.compile("=\\?([^?\\s]+)\\?([bBqQ])\\?([^?\\s]*)\\?=");
    private static final Pattern ENTITY = Pattern.compile("&(#x[0-9a-fA-F]+|#[0-9]+|[A-Za-z][A-Za-z0-9]{1,9});");
    private static final Map<String, String> NAMED = new HashMap<>();

    static {
        String[][] pairs = {{"amp", "&"}, {"lt", "<"}, {"gt", ">"}, {"quot", "\""}, {"apos", "'"}, {"nbsp", " "},
                {"aacute", "á"}, {"eacute", "é"}, {"iacute", "í"}, {"oacute", "ó"}, {"uacute", "ú"},
                {"Aacute", "Á"}, {"Eacute", "É"}, {"Iacute", "Í"}, {"Oacute", "Ó"}, {"Uacute", "Ú"},
                {"ntilde", "ñ"}, {"Ntilde", "Ñ"}, {"uuml", "ü"}, {"Uuml", "Ü"}, {"iexcl", "¡"}, {"iquest", "¿"},
                {"copy", "©"}, {"reg", "®"}, {"ordm", "º"}, {"ordf", "ª"}, {"middot", "·"}, {"bull", "•"},
                {"ndash", "–"}, {"mdash", "—"}, {"hellip", "…"}, {"euro", "€"}, {"cent", "¢"}, {"pound", "£"},
                {"agrave", "à"}, {"egrave", "è"}, {"ccedil", "ç"}};
        for (String[] p : pairs) {
            NAMED.put(p[0], p[1]);
        }
    }

    private MessageTextExtractor() {
    }

    /** Texto plano del mensaje, truncado a {@link RawEvent#MAX_RAW_TEXT_LENGTH}. */
    public static String extract(MessagePart root) {
        if (root == null) {
            return "";
        }
        List<String> plain = new ArrayList<>();
        List<String> html = new ArrayList<>();
        collect(root, plain, html);
        String text;
        if (!plain.isEmpty() && !String.join("", plain).isBlank()) {
            text = String.join("\n\n", plain);
        } else {
            List<String> converted = new ArrayList<>();
            for (String h : html) {
                converted.add(htmlToText(h));
            }
            text = String.join("\n\n", converted);
        }
        text = normalizeWhitespace(text);
        return text.length() > RawEvent.MAX_RAW_TEXT_LENGTH ? text.substring(0, RawEvent.MAX_RAW_TEXT_LENGTH) : text;
    }

    private static void collect(MessagePart part, List<String> plain, List<String> html) {
        boolean attachment = !part.filename().isEmpty();
        if (!attachment && part.parts().isEmpty()) {
            String type = part.mimeType();
            if (type.equals("text/plain") || type.equals("text/html")) {
                String s = new String(part.body(), charsetOf(part));
                (type.equals("text/plain") ? plain : html).add(s);
            }
        }
        for (MessagePart child : part.parts()) {
            collect(child, plain, html);
        }
    }

    static Charset charsetOf(MessagePart part) {
        String ct = part.headers().get("content-type");
        if (ct != null) {
            Matcher m = CHARSET.matcher(ct);
            if (m.find()) {
                try {
                    return Charset.forName(m.group(1));
                } catch (RuntimeException e) {
                    // charset desconocido: UTF-8
                }
            }
        }
        return StandardCharsets.UTF_8;
    }

    /** HTML a texto plano simple. */
    public static String htmlToText(String html) {
        String s = html;
        s = s.replaceAll("(?is)<(script|style|head)\\b.*?</\\1\\s*>", " ");
        s = s.replaceAll("(?s)<!--.*?-->", " ");
        s = s.replaceAll("(?i)<br\\s*/?>", "\n");
        s = s.replaceAll("(?i)</(p|div|tr|li|h[1-6]|table|ul|ol|section|blockquote)\\s*>", "\n");
        s = s.replaceAll("(?i)</(td|th)\\s*>", " ");
        s = s.replaceAll("(?s)<[^>]*>", "");
        return decodeEntities(s);
    }

    static String decodeEntities(String s) {
        Matcher m = ENTITY.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String ent = m.group(1);
            String rep = null;
            try {
                if (ent.startsWith("#x") || ent.startsWith("#X")) {
                    rep = new String(Character.toChars(Integer.parseInt(ent.substring(2), 16)));
                } else if (ent.startsWith("#")) {
                    rep = new String(Character.toChars(Integer.parseInt(ent.substring(1))));
                } else {
                    rep = NAMED.get(ent);
                }
            } catch (RuntimeException e) {
                rep = null;
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(rep == null ? m.group() : rep));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String normalizeWhitespace(String s) {
        s = s.replace("\r\n", "\n").replace('\r', '\n').replace('\u00a0', ' ');
        s = s.replaceAll("[\\t\\x0B\\f ]+", " ");
        s = s.replaceAll(" ?\n ?", "\n");
        s = s.replaceAll("\n{3,}", "\n\n");
        return s.strip();
    }

    /** Decodifica palabras codificadas RFC 2047 ({@code =?UTF-8?B?...?=}). */
    public static String decodeHeader(String value) {
        if (value == null || !value.contains("=?")) {
            return value;
        }
        // espacios entre palabras codificadas adyacentes se ignoran (RFC 2047 6.2)
        String v = value.replaceAll("(\\?=)\\s+(=\\?)", "$1$2");
        Matcher m = ENCODED_WORD.matcher(v);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String rep;
            try {
                Charset cs = Charset.forName(m.group(1).replaceAll("\\*.*$", ""));
                byte[] bytes = m.group(2).equalsIgnoreCase("B")
                        ? java.util.Base64.getMimeDecoder().decode(m.group(3))
                        : decodeQ(m.group(3));
                rep = new String(bytes, cs);
            } catch (RuntimeException e) {
                rep = m.group();
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(rep));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static byte[] decodeQ(String s) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '_') {
                out.write(' ');
            } else if (c == '=' && i + 2 < s.length()) {
                out.write(Integer.parseInt(s.substring(i + 1, i + 3), 16));
                i += 2;
            } else {
                out.write(c);
            }
        }
        return out.toByteArray();
    }

    /** Nombre de cabecera en minúsculas para búsquedas. */
    static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
