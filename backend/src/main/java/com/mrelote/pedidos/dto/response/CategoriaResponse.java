package com.mrelote.pedidos.dto.response;

import com.mrelote.pedidos.entity.Categoria;

public record CategoriaResponse(Long id, String nombre, Integer orden, Boolean activa) {
    public static CategoriaResponse from(Categoria c) {
        return new CategoriaResponse(c.getId(), c.getNombre(), c.getOrden(), c.getActiva());
    }
}
