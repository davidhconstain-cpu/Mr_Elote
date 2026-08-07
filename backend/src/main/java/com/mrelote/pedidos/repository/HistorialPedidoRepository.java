package com.mrelote.pedidos.repository;

import com.mrelote.pedidos.entity.HistorialPedido;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HistorialPedidoRepository extends JpaRepository<HistorialPedido, Long> {
    List<HistorialPedido> findByPedidoIdOrderByCreadoEnDesc(Long pedidoId);
}
