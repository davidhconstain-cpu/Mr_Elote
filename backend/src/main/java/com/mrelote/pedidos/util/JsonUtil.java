package com.mrelote.pedidos.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public final class JsonUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private JsonUtil() {
    }

    /** Serializa a JSON para auditoria.valorAnterior/valorNuevo; null si el valor es null. */
    public static String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return "{\"error\":\"no se pudo serializar\"}";
        }
    }
}
