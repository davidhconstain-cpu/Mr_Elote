package com.mrelote.pedidos.dto.response;

import com.mrelote.pedidos.entity.Pedido;
import com.mrelote.pedidos.entity.PedidoDetalle;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record PedidoResponse(
        Long id, String tipo, String canal, String estado,
        Long clienteId, Long mesaId, String direccionTexto,
        BigDecimal subtotal, BigDecimal totalAdicionales, BigDecimal descuento,
        BigDecimal domicilio, BigDecimal total, String observaciones,
        List<ItemResponse> items, OffsetDateTime creadoEn, OffsetDateTime actualizadoEn) {

    public record ItemResponse(
            Long id, Long productoId, Long comboId, String nombreSnapshot,
            BigDecimal precioUnitarioSnapshot, Integer cantidad, BigDecimal subtotalLinea) {
        public static ItemResponse from(PedidoDetalle d) {
            return new ItemResponse(
                    d.getId(),
                    d.getProducto() != null ? d.getProducto().getId() : null,
                    d.getCombo() != null ? d.getCombo().getId() : null,
                    d.getNombreSnapshot(), d.getPrecioUnitarioSnapshot(), d.getCantidad(), d.getSubtotalLinea());
        }
    }

    public static PedidoResponse from(Pedido p) {
        return new PedidoResponse(
                p.getId(), p.getTipo(), p.getCanal(), p.getEstado(),
                p.getCliente() != null ? p.getCliente().getUsuarioId() : null,
                p.getMesa() != null ? p.getMesa().getId() : null,
                p.getDireccionTextoSnapshot(),
                p.getSubtotal(), p.getTotalAdicionales(), p.getDescuento(), p.getDomicilio(), p.getTotal(),
                p.getObservaciones(),
                p.getItems().stream().map(ItemResponse::from).toList(),
                p.getCreadoEn(), p.getActualizadoEn());
    }
}
