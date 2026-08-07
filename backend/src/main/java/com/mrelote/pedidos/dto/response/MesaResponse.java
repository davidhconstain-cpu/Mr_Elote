package com.mrelote.pedidos.dto.response;

import com.mrelote.pedidos.entity.Mesa;

public record MesaResponse(Long id, String numero, Integer capacidad, Boolean activa) {
    public static MesaResponse from(Mesa m) {
        return new MesaResponse(m.getId(), m.getNumero(), m.getCapacidad(), m.getActiva());
    }
}
