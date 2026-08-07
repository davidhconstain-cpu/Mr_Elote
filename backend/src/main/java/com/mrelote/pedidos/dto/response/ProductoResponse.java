package com.mrelote.pedidos.dto.response;

import com.mrelote.pedidos.entity.Producto;

import java.math.BigDecimal;

public record ProductoResponse(
        Long id, Long categoriaId, String codigo, String nombre, String descripcion,
        BigDecimal precio, Boolean disponible, Boolean activo) {

    public static ProductoResponse from(Producto p) {
        return new ProductoResponse(
                p.getId(), p.getCategoria().getId(), p.getCodigo(), p.getNombre(), p.getDescripcion(),
                p.getPrecio(), p.getDisponible(), p.getActivo());
    }
}
