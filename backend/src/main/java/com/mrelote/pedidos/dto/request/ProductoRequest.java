package com.mrelote.pedidos.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record ProductoRequest(
        @NotNull Long categoriaId,
        @NotBlank String codigo,
        @NotBlank String nombre,
        String descripcion,
        @NotNull @PositiveOrZero BigDecimal precio,
        Integer porcionPersonas,
        Boolean disponible,
        Boolean activo) {
}
