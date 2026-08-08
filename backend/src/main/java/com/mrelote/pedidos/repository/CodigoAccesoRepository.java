package com.mrelote.pedidos.repository;

import com.mrelote.pedidos.entity.CodigoAcceso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface CodigoAccesoRepository extends JpaRepository<CodigoAcceso, Long> {

    /** El código más reciente y todavía sin usar de ese usuario. */
    Optional<CodigoAcceso> findFirstByUsuarioIdAndUsadoEnIsNullOrderByCreadoEnDesc(Long usuarioId);

    /**
     * Consumo atómico: solo tiene efecto si el código sigue sin usar, así
     * que dos peticiones simultáneas con el mismo código no pueden ambas
     * iniciar sesión (devuelve 0 filas para la que llegue segunda).
     */
    @Modifying
    @Transactional
    @Query("UPDATE CodigoAcceso c SET c.usadoEn = :ahora WHERE c.id = :id AND c.usadoEn IS NULL")
    int marcarUsado(@Param("id") Long id, @Param("ahora") OffsetDateTime ahora);

    /**
     * Transacción propia (REQUIRES_NEW no haría falta porque el servicio no
     * abre una): el intento fallido debe quedar registrado aunque la
     * validación termine lanzando excepción — si viviera dentro de la
     * transacción del servicio, el rollback borraría el incremento y el
     * límite de intentos nunca se alcanzaría.
     */
    @Modifying
    @Transactional
    @Query("UPDATE CodigoAcceso c SET c.intentos = c.intentos + 1 WHERE c.id = :id")
    int registrarIntentoFallido(@Param("id") Long id);
}
