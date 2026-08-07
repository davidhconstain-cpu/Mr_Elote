package com.mrelote.pedidos.repository;

import com.mrelote.pedidos.entity.ComboDisponibilidad;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Consulta directa contra la vista combo_disponibilidad definida en el
 * esquema SQL (database/schema.sql, sección 11 v1.1): un combo está
 * disponible si cada grupo de componentes tiene al menos un producto
 * disponible.
 */
public interface ComboDisponibilidadRepository extends JpaRepository<ComboDisponibilidad, Long> {
    Optional<ComboDisponibilidad> findByComboId(Long comboId);
    List<ComboDisponibilidad> findByComboIdIn(List<Long> comboIds);
}
