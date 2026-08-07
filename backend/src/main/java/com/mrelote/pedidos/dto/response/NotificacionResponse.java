package com.mrelote.pedidos.dto.response;

import com.mrelote.pedidos.entity.Notificacion;

import java.time.OffsetDateTime;

public record NotificacionResponse(
        Long id, Long pedidoId, String canal, String tipo, String contenido, String estado,
        OffsetDateTime creadoEn, OffsetDateTime enviadoEn) {

    public static NotificacionResponse from(Notificacion n) {
        return new NotificacionResponse(
                n.getId(), n.getPedido() != null ? n.getPedido().getId() : null,
                n.getCanal(), n.getTipo(), n.getContenido(), n.getEstado(), n.getCreadoEn(), n.getEnviadoEn());
    }
}
