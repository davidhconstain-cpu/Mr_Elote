package com.mrelote.pedidos.repository;

import com.mrelote.pedidos.entity.ProductoOpcion;
import com.mrelote.pedidos.entity.ProductoOpcionId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductoOpcionRepository extends JpaRepository<ProductoOpcion, ProductoOpcionId> {
    List<ProductoOpcion> findByProductoId(Long productoId);
}
