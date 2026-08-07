package com.mrelote.pedidos.repository;

import com.mrelote.pedidos.entity.Configuracion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfiguracionRepository extends JpaRepository<Configuracion, String> {
}
