package com.mrelote.pedidos.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CrearUsuarioRequest(
        @NotBlank String nombre,
        @NotBlank @Email String email,
        String telefono,
        @NotBlank String password,
        @NotNull Long rolId) {
}
