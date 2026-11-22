package com.vivanetpay.validation;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Normalización de texto para comparaciones y huellas: minúsculas, sin tildes, espacios colapsados. */
public final class TextNormalizer {
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private TextNormalizer() {
    }

    public static String normalize(String s) {
        if (s == null) {
            return "";
        }
        String n = Normalizer.normalize(s, Normalizer.Form.NFD);
        n = MARKS.matcher(n).replaceAll("");
        n = SPACES.matcher(n.toLowerCase(Locale.ROOT)).replaceAll(" ");
        return n.trim();
    }
}
