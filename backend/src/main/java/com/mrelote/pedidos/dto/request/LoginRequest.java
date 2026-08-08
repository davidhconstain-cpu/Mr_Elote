package com.mrelote.pedidos.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * El identificador puede ser el correo o el número de documento (ambos son
 * únicos). Por eso ya no lleva @Email: un documento no lo cumpliría.
 */
public record LoginRequest(
        @NotBlank(message = "Ingresa tu correo o número de documento") String identificador,
        @NotBlank String password) {
}
