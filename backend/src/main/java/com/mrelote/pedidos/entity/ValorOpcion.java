package com.mrelote.pedidos.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "valor_opcion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValorOpcion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "opcion_id", nullable = false)
    private Opcion opcion;

    @Column(nullable = false)
    private String nombre;

    @Column(name = "precio_adicional", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal precioAdicional = BigDecimal.ZERO;
}
