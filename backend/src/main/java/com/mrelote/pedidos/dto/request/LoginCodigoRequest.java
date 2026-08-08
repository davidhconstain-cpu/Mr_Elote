package com.mrelote.pedidos.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LoginCodigoRequest(
        @NotBlank(message = "Ingresa tu correo o número de documento") String identificador,
        @NotBlank(message = "Ingresa el código que recibiste") String codigo) {
}
