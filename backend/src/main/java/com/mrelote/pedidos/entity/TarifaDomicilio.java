package com.mrelote.pedidos.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Versionada en el tiempo (vigenteDesde/vigenteHasta) en vez de sobrescribirse:
 * un pedido ya despachado no debe cambiar de precio si la tarifa de la zona
 * se actualiza después (RN-005, sección 18).
 */
@Entity
@Table(name = "tarifa_domicilio")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TarifaDomicilio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "zona_domicilio_id", nullable = false)
    private ZonaDomicilio zonaDomicilio;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal tarifa;

    @Column(name = "tiempo_estimado_minutos")
    private Integer tiempoEstimadoMinutos;

    @Column(name = "vigente_desde")
    @Builder.Default
    private OffsetDateTime vigenteDesde = OffsetDateTime.now();

    @Column(name = "vigente_hasta")
    private OffsetDateTime vigenteHasta;
}
