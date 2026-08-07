package com.mrelote.pedidos.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record ZonaDomicilioRequest(
        @NotBlank String nombre,
        Boolean activa,
        @NotNull @PositiveOrZero BigDecimal tarifa,
        Integer tiempoEstimadoMinutos) {
}
