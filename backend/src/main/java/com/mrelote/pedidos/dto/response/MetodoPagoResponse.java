package com.mrelote.pedidos.dto.response;

import com.mrelote.pedidos.entity.MetodoPago;

public record MetodoPagoResponse(Long id, String nombre, Boolean activo) {
    public static MetodoPagoResponse from(MetodoPago m) {
        return new MetodoPagoResponse(m.getId(), m.getNombre(), m.getActivo());
    }
}
