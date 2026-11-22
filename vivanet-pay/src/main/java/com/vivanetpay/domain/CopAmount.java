package com.vivanetpay.domain;

/**
 * Reglas y presentación de importes COP. El valor es SIEMPRE un {@code long} en pesos enteros
 * (RN-007); nunca {@code double}. Los separadores de miles son solo presentación.
 */
public final class CopAmount {
    /** Única moneda admitida en el MVP (RN-006). */
    public static final String CURRENCY = "COP";
    /** Límite superior de cordura para un importe (configurable a futuro): 10.000 millones. */
    public static final long MAX_REASONABLE = 10_000_000_000L;

    private CopAmount() {
    }

    /** @return true si el importe es aceptable para contabilizarse (positivo y razonable). */
    public static boolean isValid(long pesos) {
        return pesos > 0 && pesos <= MAX_REASONABLE;
    }

    /** @return true si la moneda es COP (o null, que se interpreta como COP por contexto colombiano). */
    public static boolean isSupportedCurrency(String currency) {
        return currency == null || CURRENCY.equalsIgnoreCase(currency.trim());
    }

    /** Formato colombiano sin decimales: 125000 -> "$125.000" (IDD §9.4). */
    public static String format(long pesos) {
        return "$" + group(pesos);
    }

    /** Agrupa miles con punto: 1250000 -> "1.250.000". */
    public static String group(long pesos) {
        String digits = Long.toString(Math.abs(pesos));
        StringBuilder sb = new StringBuilder();
        int first = digits.length() % 3;
        if (first > 0) {
            sb.append(digits, 0, first);
        }
        for (int i = first; i < digits.length(); i += 3) {
            if (sb.length() > 0) {
                sb.append('.');
            }
            sb.append(digits, i, i + 3);
        }
        return (pesos < 0 ? "-" : "") + sb;
    }
}
