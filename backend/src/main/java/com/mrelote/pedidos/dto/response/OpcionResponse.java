package com.mrelote.pedidos.dto.response;

import com.mrelote.pedidos.entity.Opcion;
import com.mrelote.pedidos.entity.ValorOpcion;

import java.math.BigDecimal;
import java.util.List;

public record OpcionResponse(Long id, String nombre, String tipo, List<ValorResponse> valores) {

    public record ValorResponse(Long id, String nombre, BigDecimal precioAdicional) {
        public static ValorResponse from(ValorOpcion v) {
            return new ValorResponse(v.getId(), v.getNombre(), v.getPrecioAdicional());
        }
    }

    public static OpcionResponse from(Opcion o, List<ValorOpcion> valores) {
        return new OpcionResponse(o.getId(), o.getNombre(), o.getTipo(), valores.stream().map(ValorResponse::from).toList());
    }
}
