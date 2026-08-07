package com.mrelote.pedidos.dto.response;

import com.mrelote.pedidos.entity.Rol;

import java.util.List;

public record RolResponse(Long id, String nombre, String descripcion, List<String> permisos) {
    public static RolResponse from(Rol r) {
        return new RolResponse(r.getId(), r.getNombre(), r.getDescripcion(),
                r.getPermisos().stream().map(com.mrelote.pedidos.entity.Permiso::getCodigo).toList());
    }
}
