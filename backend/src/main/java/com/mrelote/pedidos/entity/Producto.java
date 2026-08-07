package com.mrelote.pedidos.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Un producto agotado (disponible = false) permanece visible en el catálogo,
 * nunca se oculta ni se borra (RN-004).
 */
@Entity
@Table(name = "producto")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    @Column(nullable = false, unique = true)
    private String codigo;

    @Column(nullable = false)
    private String nombre;

    private String descripcion;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precio;

    /** Para cuántas personas rinde esta porción (ver V5__add_porcion_personas.sql). */
    @Column(name = "porcion_personas")
    private Integer porcionPersonas;

    @Builder.Default
    private Boolean disponible = true;

    @Builder.Default
    private Boolean activo = true;

    @Column(name = "creado_en")
    @Builder.Default
    private OffsetDateTime creadoEn = OffsetDateTime.now();

    @Column(name = "actualizado_en")
    @Builder.Default
    private OffsetDateTime actualizadoEn = OffsetDateTime.now();

    @OneToMany(mappedBy = "producto")
    @Builder.Default
    private Set<ImagenProducto> imagenes = new HashSet<>();

    @ManyToMany
    @JoinTable(
            name = "producto_adicional",
            joinColumns = @JoinColumn(name = "producto_id"),
            inverseJoinColumns = @JoinColumn(name = "adicional_id"))
    @Builder.Default
    private Set<Adicional> adicionales = new HashSet<>();

    @OneToMany(mappedBy = "producto")
    @Builder.Default
    private Set<ProductoOpcion> productoOpciones = new HashSet<>();

    @PreUpdate
    void onUpdate() {
        this.actualizadoEn = OffsetDateTime.now();
    }
}
