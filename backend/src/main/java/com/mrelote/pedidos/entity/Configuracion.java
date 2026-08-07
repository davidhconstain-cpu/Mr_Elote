package com.mrelote.pedidos.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "configuracion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Configuracion {

    @Id
    private String clave;

    @Column(nullable = false)
    private String valor;

    private String descripcion;

    @Column(name = "actualizado_en")
    @Builder.Default
    private OffsetDateTime actualizadoEn = OffsetDateTime.now();

    @PreUpdate
    void onUpdate() {
        this.actualizadoEn = OffsetDateTime.now();
    }
}
