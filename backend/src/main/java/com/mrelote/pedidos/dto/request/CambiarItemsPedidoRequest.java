package com.mrelote.pedidos.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CambiarItemsPedidoRequest(
        @NotEmpty List<ItemPedidoRequest> items,
        @NotBlank String motivo) {
}
