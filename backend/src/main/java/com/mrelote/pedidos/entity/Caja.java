package com.mrelote.pedidos.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "caja")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Caja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "usuario_apertura_id", nullable = false)
    private Usuario usuarioApertura;

    @ManyToOne
    @JoinColumn(name = "usuario_cierre_id")
    private Usuario usuarioCierre;

    @Column(name = "monto_apertura", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoApertura;

    @Column(nullable = false)
    @Builder.Default
    private String estado = "abierta";

    @Column(name = "monto_esperado", precision = 12, scale = 2)
    private BigDecimal montoEsperado;

    @Column(name = "monto_real", precision = 12, scale = 2)
    private BigDecimal montoReal;

    @Column(precision = 12, scale = 2)
    private BigDecimal diferencia;

    @Column(name = "abierta_en")
    @Builder.Default
    private OffsetDateTime abiertaEn = OffsetDateTime.now();

    @Column(name = "cerrada_en")
    private OffsetDateTime cerradaEn;
}
