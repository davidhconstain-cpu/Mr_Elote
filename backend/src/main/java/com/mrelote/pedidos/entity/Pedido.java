package com.mrelote.pedidos.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * El pedido nunca se borra físicamente (RN-001). tipo/canal/estado se
 * modelan como String, no enum de JPA, porque deben coincidir literalmente
 * con los valores de los CHECK constraints en la base de datos; ver
 * com.mrelote.pedidos.domain.EstadoPedido para las constantes y las
 * transiciones válidas por rol.
 */
@Entity
@Table(name = "pedido")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String tipo;

    @Column(nullable = false)
    private String canal;

    @Column(nullable = false)
    @Builder.Default
    private String estado = "creado";

    @ManyToOne
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @ManyToOne
    @JoinColumn(name = "mesa_id")
    private Mesa mesa;

    @ManyToOne
    @JoinColumn(name = "direccion_id")
    private Direccion direccion;

    @Column(name = "direccion_texto_snapshot")
    private String direccionTextoSnapshot;

    @Column(name = "zona_domicilio_snapshot")
    private String zonaDomicilioSnapshot;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal domicilio = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "total_adicionales", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalAdicionales = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal descuento = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal total = BigDecimal.ZERO;

    private String observaciones;

    @ManyToOne
    @JoinColumn(name = "creado_por")
    private Usuario creadoPor;

    @Column(name = "creado_en")
    @Builder.Default
    private OffsetDateTime creadoEn = OffsetDateTime.now();

    @Column(name = "actualizado_en")
    @Builder.Default
    private OffsetDateTime actualizadoEn = OffsetDateTime.now();

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PedidoDetalle> items = new ArrayList<>();

    @PreUpdate
    void onUpdate() {
        this.actualizadoEn = OffsetDateTime.now();
    }
}
