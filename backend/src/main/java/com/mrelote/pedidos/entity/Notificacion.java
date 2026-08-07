package com.mrelote.pedidos.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "notificacion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "pedido_id")
    private Pedido pedido;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(nullable = false)
    private String canal;

    @Column(nullable = false)
    private String tipo;

    private String contenido;

    @Column(nullable = false)
    @Builder.Default
    private String estado = "pendiente";

    @Column(name = "creado_en")
    @Builder.Default
    private OffsetDateTime creadoEn = OffsetDateTime.now();

    @Column(name = "enviado_en")
    private OffsetDateTime enviadoEn;
}
