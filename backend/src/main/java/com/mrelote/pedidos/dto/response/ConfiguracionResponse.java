package com.mrelote.pedidos.dto.response;

import com.mrelote.pedidos.entity.Configuracion;

public record ConfiguracionResponse(String clave, String valor, String descripcion) {
    public static ConfiguracionResponse from(Configuracion c) {
        return new ConfiguracionResponse(c.getClave(), c.getValor(), c.getDescripcion());
    }
}
