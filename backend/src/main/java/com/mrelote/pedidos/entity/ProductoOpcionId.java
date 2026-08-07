package com.mrelote.pedidos.entity;

import lombok.*;

import java.io.Serializable;
import java.util.Objects;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductoOpcionId implements Serializable {

    private Long producto;
    private Long opcion;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProductoOpcionId that)) return false;
        return Objects.equals(producto, that.producto) && Objects.equals(opcion, that.opcion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(producto, opcion);
    }
}
