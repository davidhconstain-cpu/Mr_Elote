package com.mrelote.pedidos.repository;

import com.mrelote.pedidos.entity.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {
}
