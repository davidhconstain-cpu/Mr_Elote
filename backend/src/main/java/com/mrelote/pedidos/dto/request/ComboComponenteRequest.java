package com.mrelote.pedidos.dto.request;

import jakarta.validation.constraints.NotNull;

public record ComboComponenteRequest(
        @NotNull Long productoId,
        String grupo,
        Integer cantidad,
        Boolean permiteExtras) {
}
