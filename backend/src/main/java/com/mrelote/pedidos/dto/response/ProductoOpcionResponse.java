package com.mrelote.pedidos.dto.response;

import com.mrelote.pedidos.entity.ProductoOpcion;
import com.mrelote.pedidos.entity.ValorOpcion;

import java.util.List;

public record ProductoOpcionResponse(Long opcionId, String nombre, String tipo, Boolean obligatoria,
                                      List<OpcionResponse.ValorResponse> valores) {

    public static ProductoOpcionResponse from(ProductoOpcion po, List<ValorOpcion> valores) {
        return new ProductoOpcionResponse(
                po.getOpcion().getId(), po.getOpcion().getNombre(), po.getOpcion().getTipo(), po.getObligatoria(),
                valores.stream().map(OpcionResponse.ValorResponse::from).toList());
    }
}
