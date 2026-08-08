package com.mrelote.pedidos.dto.response;

import com.mrelote.pedidos.entity.Usuario;

public record UsuarioResponse(
        Long id, String nombre, String apellidos, String tipoDocumento, String numeroDocumento,
        String email, String telefono, String rol, Boolean activo, Boolean aceptaPromociones) {

    public static UsuarioResponse from(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getNombre(),
                usuario.getApellidos(),
                usuario.getTipoDocumento(),
                usuario.getNumeroDocumento(),
                usuario.getEmail(),
                usuario.getTelefono(),
                usuario.getRol().getNombre(),
                usuario.getActivo(),
                usuario.getAceptaPromociones());
    }
}
