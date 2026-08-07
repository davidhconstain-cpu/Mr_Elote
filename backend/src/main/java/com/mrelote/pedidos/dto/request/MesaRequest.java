package com.mrelote.pedidos.dto.request;

import jakarta.validation.constraints.NotBlank;

public record MesaRequest(@NotBlank String numero, Integer capacidad, Boolean activa) {
}
