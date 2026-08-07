package com.mrelote.pedidos.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record ValorOpcionRequest(@NotBlank String nombre, BigDecimal precioAdicional) {
}
