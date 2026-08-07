package com.mrelote.pedidos.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "usuario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "rol_id", nullable = false)
    private Rol rol;

    @Column(nullable = false)
    private String nombre;

    @Column(unique = true)
    private String email;

    private String telefono;

    @Column(name = "password_hash")
    private String passwordHash;

    @Builder.Default
    private Boolean activo = true;

    @Column(name = "creado_en")
    @Builder.Default
    private OffsetDateTime creadoEn = OffsetDateTime.now();

    @Column(name = "actualizado_en")
    @Builder.Default
    private OffsetDateTime actualizadoEn = OffsetDateTime.now();

    @PreUpdate
    void onUpdate() {
        this.actualizadoEn = OffsetDateTime.now();
    }
}
