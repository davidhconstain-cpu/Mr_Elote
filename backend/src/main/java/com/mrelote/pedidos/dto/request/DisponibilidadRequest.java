package com.mrelote.pedidos.dto.request;

import jakarta.validation.constraints.NotNull;

public record DisponibilidadRequest(@NotNull Boolean disponible) {
}
