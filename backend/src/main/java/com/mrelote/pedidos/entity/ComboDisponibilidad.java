package com.mrelote.pedidos.entity;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

/**
 * Mapeo de solo lectura de la vista combo_disponibilidad (ver
 * database/schema.sql, sección 11 v1.1). No es una tabla real: @Immutable
 * evita que Hibernate intente hacer INSERT/UPDATE sobre ella.
 */
@Entity
@Immutable
@Table(name = "combo_disponibilidad")
@Getter
public class ComboDisponibilidad {

    @Id
    @Column(name = "combo_id")
    private Long comboId;

    private Boolean disponible;
}
