package com.mrelote.pedidos.dto.response;

import com.mrelote.pedidos.entity.Combo;
import com.mrelote.pedidos.entity.ComboDetalle;

import java.math.BigDecimal;
import java.util.List;

public record ComboResponse(
        Long id, String nombre, String descripcion, BigDecimal precio,
        Boolean disponible, Boolean activo, List<ComponenteResponse> componentes) {

    public record ComponenteResponse(Long id, Long productoId, String productoNombre, String grupo, Integer cantidad) {
        public static ComponenteResponse from(ComboDetalle d) {
            return new ComponenteResponse(d.getId(), d.getProducto().getId(), d.getProducto().getNombre(),
                    d.getGrupo(), d.getCantidad());
        }
    }

    public static ComboResponse from(Combo c, boolean disponible) {
        return new ComboResponse(
                c.getId(), c.getNombre(), c.getDescripcion(), c.getPrecio(), disponible, c.getActivo(),
                c.getComponentes().stream().map(ComponenteResponse::from).toList());
    }
}
