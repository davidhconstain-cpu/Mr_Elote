package com.mrelote.pedidos.repository;

import com.mrelote.pedidos.entity.MetodoPago;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetodoPagoRepository extends JpaRepository<MetodoPago, Long> {
}
