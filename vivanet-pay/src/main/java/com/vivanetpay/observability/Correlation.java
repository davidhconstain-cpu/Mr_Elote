package com.vivanetpay.observability;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * Identificador de correlación en MDC (clave {@value #MDC_KEY}) para seguir un evento desde la recepción
 * hasta el anuncio (IDD §10.3). Restaura el valor anterior al cerrar.
 */
public final class Correlation implements AutoCloseable {
    public static final String MDC_KEY = "cid";
    private final String previous;

    private Correlation(String id) {
        this.previous = MDC.get(MDC_KEY);
        MDC.put(MDC_KEY, id);
    }

    /** Abre un ámbito con el id dado (o uno nuevo si es null). */
    public static Correlation open(String id) {
        return new Correlation(id == null || id.isBlank() ? newId() : id);
    }

    public static String newId() {
        return UUID.randomUUID().toString();
    }

    public static String current() {
        return MDC.get(MDC_KEY);
    }

    @Override
    public void close() {
        if (previous == null) {
            MDC.remove(MDC_KEY);
        } else {
            MDC.put(MDC_KEY, previous);
        }
    }
}
