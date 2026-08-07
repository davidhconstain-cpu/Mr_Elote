package com.mrelote.pedidos.dto.response;

import java.math.BigDecimal;

public record ProductoVendidoResponse(Long productoId, String nombre, Long unidadesVendidas, BigDecimal ventasTotal) {
}
