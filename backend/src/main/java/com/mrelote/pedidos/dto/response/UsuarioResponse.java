package com.mrelote.pedidos.dto.response;

import com.mrelote.pedidos.entity.Usuario;

public record UsuarioResponse(Long id, String nombre, String email, String telefono, String rol, Boolean activo) {

    public static UsuarioResponse from(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getNombre(),
                usuario.getEmail(),
                usuario.getTelefono(),
                usuario.getRol().getNombre(),
                usuario.getActivo());
    }
}
