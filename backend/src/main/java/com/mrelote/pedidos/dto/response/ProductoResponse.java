package com.mrelote.pedidos.dto.response;

import com.mrelote.pedidos.entity.Producto;

import java.math.BigDecimal;

public record ProductoResponse(
        Long id, Long categoriaId, String codigo, String nombre, String descripcion,
        BigDecimal precio, Integer porcionPersonas, Boolean disponible, Boolean activo,
        java.util.List<String> imagenes) {

    public static ProductoResponse from(Producto p) {
        return new ProductoResponse(
                p.getId(), p.getCategoria().getId(), p.getCodigo(), p.getNombre(), p.getDescripcion(),
                p.getPrecio(), p.getPorcionPersonas(), p.getDisponible(), p.getActivo(),
                p.getImagenes().stream()
                        .sorted(java.util.Comparator.comparing(com.mrelote.pedidos.entity.ImagenProducto::getOrden))
                        .map(com.mrelote.pedidos.entity.ImagenProducto::getUrl)
                        .toList());
    }
}
