package com.mrelote.pedidos.dto.response;

import com.mrelote.pedidos.entity.Adicional;

import java.math.BigDecimal;

public record AdicionalResponse(Long id, String nombre, BigDecimal precio, Boolean disponible) {
    public static AdicionalResponse from(Adicional a) {
        return new AdicionalResponse(a.getId(), a.getNombre(), a.getPrecio(), a.getDisponible());
    }
}
