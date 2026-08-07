package com.mrelote.pedidos.dto.response;

import com.mrelote.pedidos.entity.Pago;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PagoResponse(
        Long id, Long pedidoId, Long metodoPagoId, BigDecimal monto, String estado,
        String referenciaExterna, OffsetDateTime creadoEn, OffsetDateTime validadoEn) {

    public static PagoResponse from(Pago p) {
        return new PagoResponse(
                p.getId(), p.getPedido().getId(), p.getMetodoPago().getId(), p.getMonto(), p.getEstado(),
                p.getReferenciaExterna(), p.getCreadoEn(), p.getValidadoEn());
    }
}
