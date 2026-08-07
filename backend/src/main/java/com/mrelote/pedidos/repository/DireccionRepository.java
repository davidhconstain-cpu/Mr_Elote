package com.mrelote.pedidos.repository;

import com.mrelote.pedidos.entity.Direccion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DireccionRepository extends JpaRepository<Direccion, Long> {
    List<Direccion> findByClienteUsuarioId(Long clienteId);
}
