package com.mrelote.pedidos.dto.request;

import jakarta.validation.constraints.NotNull;

public record AsignarAdicionalRequest(@NotNull Long adicionalId) {
}
