package com.mrelote.pedidos.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record ComboRequest(
        @NotBlank String nombre,
        String descripcion,
        @NotNull @PositiveOrZero BigDecimal precio,
        Boolean activo) {
}
