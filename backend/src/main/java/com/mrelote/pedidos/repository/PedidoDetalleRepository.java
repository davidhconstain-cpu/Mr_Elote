package com.mrelote.pedidos.repository;

import com.mrelote.pedidos.entity.PedidoDetalle;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PedidoDetalleRepository extends JpaRepository<PedidoDetalle, Long> {
}
