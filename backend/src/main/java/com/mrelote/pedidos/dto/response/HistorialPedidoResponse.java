package com.mrelote.pedidos.dto.response;

import com.mrelote.pedidos.entity.HistorialPedido;

import java.time.OffsetDateTime;

public record HistorialPedidoResponse(
        Long id, Long usuarioId, String usuarioNombre, String campoModificado,
        String valorAnterior, String valorNuevo, String motivo, OffsetDateTime creadoEn) {

    public static HistorialPedidoResponse from(HistorialPedido h) {
        return new HistorialPedidoResponse(
                h.getId(),
                h.getUsuario() != null ? h.getUsuario().getId() : null,
                h.getUsuario() != null ? h.getUsuario().getNombre() : null,
                h.getCampoModificado(), h.getValorAnterior(), h.getValorNuevo(), h.getMotivo(), h.getCreadoEn());
    }
}
