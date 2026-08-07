package com.mrelote.pedidos.repository.projection;

import java.math.BigDecimal;

public interface ProductoVendidoProjection {
    Long getProductoId();
    String getNombre();
    Long getUnidadesVendidas();
    BigDecimal getVentasTotal();
}
