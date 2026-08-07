package com.mrelote.pedidos.repository;

import com.mrelote.pedidos.entity.ValorOpcion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ValorOpcionRepository extends JpaRepository<ValorOpcion, Long> {
    List<ValorOpcion> findByOpcionId(Long opcionId);
}
