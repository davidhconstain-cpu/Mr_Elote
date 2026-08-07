package com.mrelote.pedidos.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * nombreSnapshot / precioUnitarioSnapshot quedan "congelados" al momento de
 * la venta: un cambio de precio futuro en el catálogo no altera pedidos ya
 * vendidos (RN-003, RF-013).
 */
@Entity
@Table(name = "pedido_detalle")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PedidoDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @ManyToOne
    @JoinColumn(name = "producto_id")
    private Producto producto;

    @ManyToOne
    @JoinColumn(name = "combo_id")
    private Combo combo;

    @Column(name = "nombre_snapshot", nullable = false)
    private String nombreSnapshot;

    @Column(name = "precio_unitario_snapshot", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioUnitarioSnapshot;

    @Column(nullable = false)
    private Integer cantidad;

    @Column(name = "subtotal_linea", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotalLinea;

    @OneToMany(mappedBy = "pedidoDetalle", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PedidoDetalleOpcion> opciones = new ArrayList<>();

    @OneToMany(mappedBy = "pedidoDetalle", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PedidoDetalleAdicional> adicionales = new ArrayList<>();
}
