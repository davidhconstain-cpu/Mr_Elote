package com.mrelote.pedidos.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record ActualizarTarifaRequest(
        @NotNull @PositiveOrZero BigDecimal tarifa,
        Integer tiempoEstimadoMinutos) {
}
