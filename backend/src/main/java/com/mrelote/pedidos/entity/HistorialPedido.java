package com.mrelote.pedidos.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/** RN-002: toda modificación relevante de un pedido queda registrada aquí. */
@Entity
@Table(name = "historial_pedido")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistorialPedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "campo_modificado", nullable = false)
    private String campoModificado;

    @Column(name = "valor_anterior")
    private String valorAnterior;

    @Column(name = "valor_nuevo")
    private String valorNuevo;

    private String motivo;

    @Column(name = "creado_en")
    @Builder.Default
    private OffsetDateTime creadoEn = OffsetDateTime.now();
}
