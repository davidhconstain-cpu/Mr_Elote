package com.mrelote.pedidos.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "pedido_detalle_opcion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PedidoDetalleOpcion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "pedido_detalle_id", nullable = false)
    private PedidoDetalle pedidoDetalle;

    @ManyToOne
    @JoinColumn(name = "valor_opcion_id")
    private ValorOpcion valorOpcion;

    @Column(name = "nombre_snapshot", nullable = false)
    private String nombreSnapshot;

    @Column(name = "precio_adicional_snapshot", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal precioAdicionalSnapshot = BigDecimal.ZERO;
}
