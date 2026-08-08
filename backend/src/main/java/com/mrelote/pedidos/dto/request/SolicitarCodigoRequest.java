package com.mrelote.pedidos.dto.request;

import jakarta.validation.constraints.NotBlank;

public record SolicitarCodigoRequest(
        @NotBlank(message = "Ingresa tu correo o número de documento") String identificador) {
}
