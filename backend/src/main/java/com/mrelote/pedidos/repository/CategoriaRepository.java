package com.mrelote.pedidos.repository;

import com.mrelote.pedidos.entity.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {
}
