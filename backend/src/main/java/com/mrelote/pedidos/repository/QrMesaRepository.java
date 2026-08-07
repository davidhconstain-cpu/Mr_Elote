package com.mrelote.pedidos.repository;

import com.mrelote.pedidos.entity.QrMesa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QrMesaRepository extends JpaRepository<QrMesa, Long> {
    Optional<QrMesa> findByCodigoAndActivoTrue(String codigo);
    Optional<QrMesa> findByMesaIdAndActivoTrue(Long mesaId);
}
