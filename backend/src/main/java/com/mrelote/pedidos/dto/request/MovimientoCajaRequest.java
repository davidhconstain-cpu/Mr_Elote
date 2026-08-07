package com.mrelote.pedidos.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record MovimientoCajaRequest(
        @Pattern(regexp = "ingreso|egreso") String tipo,
        @NotNull BigDecimal monto,
        String descripcion) {
}
