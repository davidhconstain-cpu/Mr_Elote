package com.mrelote.pedidos.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record AbrirCajaRequest(@NotNull @PositiveOrZero BigDecimal montoApertura) {
}
