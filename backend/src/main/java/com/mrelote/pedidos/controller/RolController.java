package com.mrelote.pedidos.controller;

import com.mrelote.pedidos.dto.response.RolResponse;
import com.mrelote.pedidos.service.RolService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('Administrador')")
public class RolController {

    private final RolService rolService;

    @GetMapping
    public List<RolResponse> listar() {
        return rolService.listar();
    }

    @PutMapping("/{id}/permisos")
    public RolResponse asignarPermisos(@PathVariable Long id, @RequestBody List<Long> permisoIds, Authentication auth) {
        return rolService.asignarPermisos(id, permisoIds, auth);
    }
}
