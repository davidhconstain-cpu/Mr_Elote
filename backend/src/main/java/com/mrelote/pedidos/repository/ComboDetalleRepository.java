package com.mrelote.pedidos.repository;

import com.mrelote.pedidos.entity.ComboDetalle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComboDetalleRepository extends JpaRepository<ComboDetalle, Long> {
    List<ComboDetalle> findByComboId(Long comboId);
    void deleteByComboId(Long comboId);
}
