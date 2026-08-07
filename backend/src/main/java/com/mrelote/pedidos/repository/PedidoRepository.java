package com.mrelote.pedidos.repository;

import com.mrelote.pedidos.entity.Pedido;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface PedidoRepository extends JpaRepository<Pedido, Long>, JpaSpecificationExecutor<Pedido> {

    /** Cola de cocina: solo pedidos habilitados, ordenados por hora de entrada (RF-018 a RF-021). */
    List<Pedido> findByTipoAndEstadoInOrderByCreadoEnAsc(String tipo, List<String> estados);

    List<Pedido> findByEstadoInOrderByCreadoEnAsc(List<String> estados);

    Page<Pedido> findByClienteUsuarioIdOrderByCreadoEnDesc(Long clienteId, Pageable pageable);
}
