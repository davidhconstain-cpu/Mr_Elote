package com.mrelote.pedidos.repository;

import com.mrelote.pedidos.entity.Caja;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CajaRepository extends JpaRepository<Caja, Long> {
    Optional<Caja> findFirstByEstadoOrderByAbiertaEnDesc(String estado);
    Page<Caja> findByEstadoOrderByAbiertaEnDesc(String estado, Pageable pageable);
}
