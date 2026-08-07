package com.mrelote.pedidos.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record CrearPedidoRequest(
        @NotNull @Pattern(regexp = "local|recoger|domicilio") String tipo,
        String mesaCodigoQr,
        Long direccionId,
        @NotEmpty List<@NotNull ItemPedidoRequest> items,
        String observaciones) {
}
