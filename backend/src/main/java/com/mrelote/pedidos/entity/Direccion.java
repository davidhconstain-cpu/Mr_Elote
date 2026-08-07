package com.mrelote.pedidos.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "direccion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Direccion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @ManyToOne
    @JoinColumn(name = "zona_domicilio_id")
    private ZonaDomicilio zonaDomicilio;

    private String etiqueta;

    @Column(name = "direccion_texto", nullable = false)
    private String direccionTexto;

    private String referencia;

    @Builder.Default
    private Boolean predeterminada = false;
}
