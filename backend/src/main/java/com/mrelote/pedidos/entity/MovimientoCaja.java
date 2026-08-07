package com.mrelote.pedidos.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "movimiento_caja")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovimientoCaja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "caja_id", nullable = false)
    private Caja caja;

    @Column(nullable = false)
    private String tipo;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @ManyToOne
    @JoinColumn(name = "pedido_id")
    private Pedido pedido;

    private String descripcion;

    @ManyToOne(optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "creado_en")
    @Builder.Default
    private OffsetDateTime creadoEn = OffsetDateTime.now();
}
