package com.mrelote.pedidos.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "imagen_producto")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImagenProducto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(nullable = false)
    private String url;

    @Builder.Default
    private Integer orden = 0;
}
