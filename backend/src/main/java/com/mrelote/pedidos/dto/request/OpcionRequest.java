package com.mrelote.pedidos.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OpcionRequest(
        @NotBlank String nombre,
        @Pattern(regexp = "unica|multiple") String tipo) {
}
