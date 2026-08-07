package com.mrelote.pedidos.dto.response;

import com.mrelote.pedidos.entity.Despacho;

import java.time.OffsetDateTime;

public record DespachoResponse(
        Long id, Long pedidoId, Long domiciliarioId, String domiciliarioNombre,
        String estado, OffsetDateTime asignadoEn, OffsetDateTime entregadoEn) {

    public static DespachoResponse from(Despacho d) {
        return new DespachoResponse(
                d.getId(), d.getPedido().getId(),
                d.getDomiciliario() != null ? d.getDomiciliario().getId() : null,
                d.getDomiciliario() != null ? d.getDomiciliario().getNombre() : null,
                d.getEstado(), d.getAsignadoEn(), d.getEntregadoEn());
    }
}
