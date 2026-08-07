package com.mrelote.pedidos.dto.request;

import jakarta.validation.constraints.Min;

import java.util.List;

/** Debe incluir productoId o comboId, nunca ambos (se valida en el servicio). */
public record ItemPedidoRequest(
        Long productoId,
        Long comboId,
        @Min(1) Integer cantidad,
        List<Long> valoresOpcionId,
        List<Long> adicionalesId,
        String observaciones) {
}
