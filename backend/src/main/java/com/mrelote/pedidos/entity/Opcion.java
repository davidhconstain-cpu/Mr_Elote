package com.mrelote.pedidos.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "opcion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Opcion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    /** "unica" o "multiple" — ver constraint chk en la tabla opcion. */
    @Column(nullable = false)
    private String tipo;
}
