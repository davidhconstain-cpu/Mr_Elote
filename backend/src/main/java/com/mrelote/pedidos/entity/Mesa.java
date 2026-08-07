package com.mrelote.pedidos.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "mesa")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Mesa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String numero;

    private Integer capacidad;

    @Builder.Default
    private Boolean activa = true;
}
