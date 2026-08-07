package com.mrelote.pedidos.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "despacho")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Despacho {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "pedido_id", nullable = false, unique = true)
    private Pedido pedido;

    @ManyToOne
    @JoinColumn(name = "domiciliario_id")
    private Usuario domiciliario;

    @Column(nullable = false)
    @Builder.Default
    private String estado = "asignado";

    @Column(name = "asignado_en")
    @Builder.Default
    private OffsetDateTime asignadoEn = OffsetDateTime.now();

    @Column(name = "entregado_en")
    private OffsetDateTime entregadoEn;
}
