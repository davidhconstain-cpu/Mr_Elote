package com.mrelote.pedidos.dto.response;

import com.mrelote.pedidos.entity.Permiso;

public record PermisoResponse(Long id, String codigo, String descripcion) {
    public static PermisoResponse from(Permiso p) {
        return new PermisoResponse(p.getId(), p.getCodigo(), p.getDescripcion());
    }
}
