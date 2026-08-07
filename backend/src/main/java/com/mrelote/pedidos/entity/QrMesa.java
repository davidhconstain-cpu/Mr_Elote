package com.mrelote.pedidos.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "qr_mesa")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QrMesa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "mesa_id", nullable = false)
    private Mesa mesa;

    @Column(nullable = false, unique = true)
    private String codigo;

    @Builder.Default
    private Boolean activo = true;

    @Column(name = "creado_en")
    @Builder.Default
    private OffsetDateTime creadoEn = OffsetDateTime.now();
}
