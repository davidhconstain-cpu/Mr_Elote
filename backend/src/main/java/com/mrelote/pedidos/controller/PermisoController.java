package com.mrelote.pedidos.controller;

import com.mrelote.pedidos.dto.response.PermisoResponse;
import com.mrelote.pedidos.repository.PermisoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** RF-003: catálogo de permisos finos disponibles para asignar a un rol. */
@RestController
@RequestMapping("/api/v1/permisos")
@RequiredArgsConstructor
@PreAuthorize("hasRole('Administrador')")
public class PermisoController {

    private final PermisoRepository permisoRepository;

    @GetMapping
    public List<PermisoResponse> listar() {
        return permisoRepository.findAll().stream().map(PermisoResponse::from).toList();
    }
}
