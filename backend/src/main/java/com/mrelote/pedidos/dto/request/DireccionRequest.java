package com.mrelote.pedidos.dto.request;

public record DireccionRequest(
        String etiqueta,
        String direccionTexto,
        Long zonaDomicilioId,
        String referencia,
        Boolean predeterminada) {
}
