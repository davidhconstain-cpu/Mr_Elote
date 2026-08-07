package com.mrelote.pedidos.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * Bitácora general del sistema (usuarios, catálogo, precios, permisos,
 * configuración...), no solo pedidos (RN-002, sección 20). `entidad` +
 * `entidadId` identifican el registro afectado sin FK física, para que la
 * auditoría sobreviva aunque el registro se elimine lógicamente.
 */
@Entity
@Table(name = "auditoria")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Auditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(nullable = false)
    private String accion;

    @Column(nullable = false)
    private String entidad;

    @Column(name = "entidad_id")
    private String entidadId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "valor_anterior", columnDefinition = "jsonb")
    private String valorAnterior;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "valor_nuevo", columnDefinition = "jsonb")
    private String valorNuevo;

    private String motivo;

    private String origen;

    @Column(name = "creado_en")
    @Builder.Default
    private OffsetDateTime creadoEn = OffsetDateTime.now();
}
