package com.mrelote.pedidos.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record AdicionalRequest(
        @NotBlank String nombre,
        @NotNull @PositiveOrZero BigDecimal precio,
        Boolean disponible) {
}
