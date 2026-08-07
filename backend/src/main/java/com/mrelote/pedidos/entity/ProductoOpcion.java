package com.mrelote.pedidos.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "producto_opcion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(ProductoOpcionId.class)
public class ProductoOpcion {

    @Id
    @ManyToOne(optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Id
    @ManyToOne(optional = false)
    @JoinColumn(name = "opcion_id", nullable = false)
    private Opcion opcion;

    @Builder.Default
    private Boolean obligatoria = false;
}
