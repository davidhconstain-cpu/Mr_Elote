package com.mrelote.pedidos.repository;

import com.mrelote.pedidos.entity.Producto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductoRepository extends JpaRepository<Producto, Long> {
    Page<Producto> findByCategoriaId(Long categoriaId, Pageable pageable);
    Page<Producto> findByDisponible(Boolean disponible, Pageable pageable);
    Page<Producto> findByCategoriaIdAndDisponible(Long categoriaId, Boolean disponible, Pageable pageable);
    boolean existsByCodigo(String codigo);
}
