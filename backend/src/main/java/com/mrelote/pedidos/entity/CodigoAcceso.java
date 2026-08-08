package com.mrelote.pedidos.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Código de un solo uso para iniciar sesión sin contraseña. Igual que
 * {@link PasswordResetToken}, solo se guarda el hash SHA-256 del código.
 */
@Entity
@Table(name = "codigo_acceso")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodigoAcceso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "codigo_hash", nullable = false)
    private String codigoHash;

    @Column(name = "expira_en", nullable = false)
    private OffsetDateTime expiraEn;

    @Column(name = "usado_en")
    private OffsetDateTime usadoEn;

    @Builder.Default
    private Integer intentos = 0;

    @Column(name = "creado_en")
    @Builder.Default
    private OffsetDateTime creadoEn = OffsetDateTime.now();
}
