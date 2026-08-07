package com.mrelote.pedidos.dto.response;

import java.util.List;

/** Coincide con el esquema Error de api/openapi.yaml. */
public record ErrorResponse(String codigo, String mensaje, List<String> detalles) {

    public static ErrorResponse of(String codigo, String mensaje) {
        return new ErrorResponse(codigo, mensaje, List.of());
    }

    public static ErrorResponse of(String codigo, String mensaje, List<String> detalles) {
        return new ErrorResponse(codigo, mensaje, detalles);
    }
}
