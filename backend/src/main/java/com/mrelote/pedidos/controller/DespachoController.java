package com.mrelote.pedidos.controller;

import com.mrelote.pedidos.dto.response.DespachoResponse;
import com.mrelote.pedidos.service.DespachoService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/despachos")
@RequiredArgsConstructor
@PreAuthorize("hasRole('Despachos') or hasRole('Administrador')")
public class DespachoController {

    private final DespachoService despachoService;

    @GetMapping
    public List<DespachoResponse> listar() {
        return despachoService.listar();
    }

    @PostMapping("/{id}/asignar")
    public DespachoResponse asignar(@PathVariable Long id, @RequestBody(required = false) Map<String, Long> body, Authentication auth) {
        Long domiciliarioId = body != null ? body.get("domiciliarioId") : null;
        return despachoService.asignar(id, domiciliarioId, auth);
    }

    @PostMapping("/{id}/en-camino")
    public DespachoResponse enCamino(@PathVariable Long id) {
        return despachoService.marcarEnCamino(id);
    }

    @PostMapping("/{id}/entregar")
    public DespachoResponse entregar(@PathVariable Long id) {
        return despachoService.marcarEntregado(id);
    }
}
