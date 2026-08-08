package com.mrelote.pedidos.repository;

import com.mrelote.pedidos.entity.Notificacion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {
    Page<Notificacion> findByUsuarioIdOrderByCreadoEnDesc(Long usuarioId, Pageable pageable);
    List<Notificacion> findByEstado(String estado);
}
