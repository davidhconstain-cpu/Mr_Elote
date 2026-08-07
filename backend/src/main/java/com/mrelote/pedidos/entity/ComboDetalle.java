package com.mrelote.pedidos.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * grupo == null: componente fijo obligatorio (si su producto se agota, el
 * combo se agota). grupo no nulo: componentes alternativos entre sí (ej.
 * "bebida"); el combo solo se agota si TODOS los del mismo grupo lo están.
 * Ver combo_disponibilidad en el esquema SQL y ComboDisponibilidadService.
 */
@Entity
@Table(name = "combo_detalle")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComboDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "combo_id", nullable = false)
    private Combo combo;

    @ManyToOne(optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    private String grupo;

    @Builder.Default
    private Integer cantidad = 1;

    @Column(name = "permite_extras")
    @Builder.Default
    private Boolean permiteExtras = false;
}
