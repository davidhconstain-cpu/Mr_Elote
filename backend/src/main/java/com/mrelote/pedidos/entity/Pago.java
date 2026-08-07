package com.mrelote.pedidos.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Vive separado del pedido con su propio estado (sección 8): cocina puede
 * depender solo del estado de pago sin acoplarse a cómo se pagó.
 * referenciaExterna es única para procesar webhooks de pasarela de forma
 * idempotente (RNF-007, RNF-008).
 */
@Entity
@Table(name = "pago")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @ManyToOne(optional = false)
    @JoinColumn(name = "metodo_pago_id", nullable = false)
    private MetodoPago metodoPago;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Column(nullable = false)
    @Builder.Default
    private String estado = "pendiente";

    @Column(name = "referencia_externa", unique = true)
    private String referenciaExterna;

    @ManyToOne
    @JoinColumn(name = "validado_por")
    private Usuario validadoPor;

    @Column(name = "creado_en")
    @Builder.Default
    private OffsetDateTime creadoEn = OffsetDateTime.now();

    @Column(name = "validado_en")
    private OffsetDateTime validadoEn;
}
