package com.mrelote.pedidos.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record RegistrarPagoRequest(
        @NotNull Long metodoPagoId,
        @NotNull @PositiveOrZero BigDecimal monto,
        String referenciaExterna) {
}
