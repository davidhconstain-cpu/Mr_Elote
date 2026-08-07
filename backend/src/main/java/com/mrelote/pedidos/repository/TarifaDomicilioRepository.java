package com.mrelote.pedidos.repository;

import com.mrelote.pedidos.entity.TarifaDomicilio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TarifaDomicilioRepository extends JpaRepository<TarifaDomicilio, Long> {

    /** La tarifa vigente de una zona es la que no tiene vigenteHasta (RN-005). */
    Optional<TarifaDomicilio> findByZonaDomicilioIdAndVigenteHastaIsNull(Long zonaDomicilioId);
}
