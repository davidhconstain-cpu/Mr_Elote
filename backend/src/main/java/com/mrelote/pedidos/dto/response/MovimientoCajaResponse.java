package com.mrelote.pedidos.dto.response;

import com.mrelote.pedidos.entity.MovimientoCaja;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record MovimientoCajaResponse(
        Long id, String tipo, BigDecimal monto, Long pedidoId, String descripcion,
        String usuarioNombre, OffsetDateTime creadoEn) {

    public static MovimientoCajaResponse from(MovimientoCaja m) {
        return new MovimientoCajaResponse(
                m.getId(), m.getTipo(), m.getMonto(),
                m.getPedido() != null ? m.getPedido().getId() : null,
                m.getDescripcion(), m.getUsuario().getNombre(), m.getCreadoEn());
    }
}
