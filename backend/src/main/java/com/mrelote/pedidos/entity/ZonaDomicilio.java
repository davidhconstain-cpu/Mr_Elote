package com.mrelote.pedidos.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "zona_domicilio")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZonaDomicilio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String nombre;

    @Builder.Default
    private Boolean activa = true;
}
