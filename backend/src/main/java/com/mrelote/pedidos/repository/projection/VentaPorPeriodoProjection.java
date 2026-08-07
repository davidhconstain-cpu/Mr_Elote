package com.mrelote.pedidos.repository.projection;

import java.math.BigDecimal;

public interface VentaPorPeriodoProjection {
    String getPeriodo();
    Long getNumeroPedidos();
    BigDecimal getVentasTotal();
    BigDecimal getTicketPromedio();
}
