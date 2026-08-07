package com.mrelote.pedidos.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "pedido_detalle_adicional")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PedidoDetalleAdicional {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "pedido_detalle_id", nullable = false)
    private PedidoDetalle pedidoDetalle;

    @ManyToOne
    @JoinColumn(name = "adicional_id")
    private Adicional adicional;

    @Column(name = "nombre_snapshot", nullable = false)
    private String nombreSnapshot;

    @Column(name = "precio_snapshot", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal precioSnapshot = BigDecimal.ZERO;

    @Builder.Default
    private Integer cantidad = 1;
}
