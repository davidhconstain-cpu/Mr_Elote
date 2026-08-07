package com.mrelote.pedidos.repository;

import com.mrelote.pedidos.entity.Opcion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OpcionRepository extends JpaRepository<Opcion, Long> {
}
