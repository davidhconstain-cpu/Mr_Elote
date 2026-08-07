package com.mrelote.pedidos.repository;

import com.mrelote.pedidos.entity.Pago;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PagoRepository extends JpaRepository<Pago, Long> {

    /** RNF-007/RNF-008: procesar el webhook de pasarela de forma idempotente. */
    Optional<Pago> findByReferenciaExterna(String referenciaExterna);

    List<Pago> findByPedidoId(Long pedidoId);
}
