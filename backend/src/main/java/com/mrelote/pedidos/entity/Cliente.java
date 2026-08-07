package com.mrelote.pedidos.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.domain.Persistable;

import java.time.LocalDate;

/**
 * Implementa Persistable: al usar @MapsId el id nunca es null (se deriva de
 * usuario), así que el chequeo por defecto de Spring Data JPA ("id == null
 * -> nuevo") concluiría que esta entidad ya existe y llamaría a merge() en
 * vez de persist() para un Cliente recién creado, lo que revienta con
 * "AssertionFailure: null identifier" al resolver la asociación. isNew()
 * explícito evita eso.
 */
@Entity
@Table(name = "cliente")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cliente implements Persistable<Long> {

    @Id
    @Column(name = "usuario_id")
    private Long usuarioId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "fecha_nacimiento")
    private LocalDate fechaNacimiento;

    private String notas;

    @Transient
    @Builder.Default
    private boolean nuevo = true;

    @Override
    public Long getId() {
        return usuarioId;
    }

    @Override
    public boolean isNew() {
        return nuevo;
    }

    @PostLoad
    @PostPersist
    void marcarComoExistente() {
        this.nuevo = false;
    }
}
