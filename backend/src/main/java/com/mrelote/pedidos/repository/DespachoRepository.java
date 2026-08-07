package com.mrelote.pedidos.repository;

import com.mrelote.pedidos.entity.Despacho;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DespachoRepository extends JpaRepository<Despacho, Long> {
    Optional<Despacho> findByPedidoId(Long pedidoId);
    List<Despacho> findByEstadoInOrderByAsignadoEnAsc(List<String> estados);
}
