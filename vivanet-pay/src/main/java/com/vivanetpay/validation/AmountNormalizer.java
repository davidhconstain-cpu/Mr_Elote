package com.vivanetpay.validation;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Convierte importes escritos en texto a pesos COP enteros (RF-009, RN-007), sin depender del formato visual.
 * <p>
 * Acepta: {@code "$50.000"}, {@code "COP 1.250.000"}, {@code "$ 1.250.000,00"}, {@code "50000"},
 * {@code "1,250,000"}, {@code "50.000,00"}, {@code "$50,000.00"}. Convención colombiana: el punto separa miles
 * y la coma decimales; si aparecen ambos, el último separador es el decimal.
 * <p>
 * Rechaza (devuelve vacío): textos sin dígitos, signos negativos, decimales distintos de cero (COP sin centavos:
 * un importe con centavos no se redondea silenciosamente, va a revisión), agrupaciones de miles inválidas
 * ("1.25.000") y desbordamientos.
 */
public final class AmountNormalizer {
    private static final Pattern STRIP = Pattern.compile("(?i)(cop|col\\$|\\$|pesos|\\s|\\u00A0)");
    private static final Pattern VALID_CHARS = Pattern.compile("[0-9.,]+");
    private static final Pattern GROUPED = Pattern.compile("\\d{1,3}(?:[.,]\\d{3})+");

    private AmountNormalizer() {
    }

    /**
     * @param text importe en texto (puede incluir símbolo $ o "COP")
     * @return pesos enteros, o vacío si el texto no es un importe COP entero inequívoco
     */
    public static Optional<Long> parsePesos(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String s = STRIP.matcher(text.trim()).replaceAll("");
        if (s.isEmpty() || !VALID_CHARS.matcher(s).matches() || !Character.isDigit(s.charAt(0))
                || !Character.isDigit(s.charAt(s.length() - 1))) {
            return Optional.empty();
        }
        String integerPart = s;
        String decimalPart = "";
        int lastDot = s.lastIndexOf('.');
        int lastComma = s.lastIndexOf(',');
        if (lastDot >= 0 && lastComma >= 0) {
            int dec = Math.max(lastDot, lastComma);
            integerPart = s.substring(0, dec);
            decimalPart = s.substring(dec + 1);
            char thousands = dec == lastDot ? ',' : '.';
            char decimalSep = s.charAt(dec);
            if (integerPart.indexOf(decimalSep) >= 0) {
                return Optional.empty();
            }
            if (!isGroupedOrPlain(integerPart, thousands)) {
                return Optional.empty();
            }
        } else if (lastDot >= 0 || lastComma >= 0) {
            char sep = lastDot >= 0 ? '.' : ',';
            int count = s.length() - s.replace(String.valueOf(sep), "").length();
            int last = s.lastIndexOf(sep);
            int digitsAfter = s.length() - last - 1;
            if (count == 1 && digitsAfter != 3) {
                // un único separador seguido de 1-2 dígitos => decimal
                if (digitsAfter > 2) {
                    return Optional.empty();
                }
                integerPart = s.substring(0, last);
                decimalPart = s.substring(last + 1);
            } else if (!GROUPED.matcher(s).matches() || s.indexOf(sep == '.' ? ',' : '.') >= 0) {
                return Optional.empty();
            }
        }
        if (!decimalPart.isEmpty() && !decimalPart.chars().allMatch(c -> c == '0')) {
            return Optional.empty();
        }
        if (decimalPart.length() > 2) {
            return Optional.empty();
        }
        String digits = integerPart.replace(".", "").replace(",", "");
        if (digits.isEmpty() || digits.length() > 15) {
            return Optional.empty();
        }
        return Optional.of(Long.parseLong(digits));
    }

    private static boolean isGroupedOrPlain(String integerPart, char thousands) {
        if (integerPart.indexOf(thousands) < 0) {
            return integerPart.chars().allMatch(Character::isDigit);
        }
        return GROUPED.matcher(integerPart).matches();
    }
}
