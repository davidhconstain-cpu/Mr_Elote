package com.mrelote.pedidos.dto.response;

import java.math.BigDecimal;

public record ResumenVentasResponse(String periodo, Long numeroPedidos, BigDecimal ventasTotal, BigDecimal ticketPromedio) {
}
