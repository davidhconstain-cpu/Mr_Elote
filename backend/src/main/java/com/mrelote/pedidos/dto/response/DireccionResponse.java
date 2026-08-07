package com.mrelote.pedidos.dto.response;

import com.mrelote.pedidos.entity.Direccion;

public record DireccionResponse(
        Long id, String etiqueta, String direccionTexto, Long zonaDomicilioId,
        String referencia, Boolean predeterminada) {

    public static DireccionResponse from(Direccion d) {
        return new DireccionResponse(
                d.getId(), d.getEtiqueta(), d.getDireccionTexto(),
                d.getZonaDomicilio() != null ? d.getZonaDomicilio().getId() : null,
                d.getReferencia(), d.getPredeterminada());
    }
}
