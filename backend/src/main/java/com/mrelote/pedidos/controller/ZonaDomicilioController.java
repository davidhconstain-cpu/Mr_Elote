package com.mrelote.pedidos.controller;

import com.mrelote.pedidos.dto.request.ActualizarTarifaRequest;
import com.mrelote.pedidos.dto.request.ZonaDomicilioRequest;
import com.mrelote.pedidos.dto.response.ZonaDomicilioResponse;
import com.mrelote.pedidos.service.ZonaDomicilioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** RF-030, sección 18. */
@RestController
@RequestMapping("/api/v1/zonas-domicilio")
@RequiredArgsConstructor
public class ZonaDomicilioController {

    private final ZonaDomicilioService zonaDomicilioService;

    @GetMapping
    public List<ZonaDomicilioResponse> listar() {
        return zonaDomicilioService.listar();
    }

    @PostMapping
    @PreAuthorize("hasRole('Administrador')")
    public ResponseEntity<ZonaDomicilioResponse> crear(@Valid @RequestBody ZonaDomicilioRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(zonaDomicilioService.crear(request));
    }

    @PatchMapping("/{id}/tarifa")
    @PreAuthorize("hasRole('Administrador')")
    public ZonaDomicilioResponse actualizarTarifa(@PathVariable Long id, @Valid @RequestBody ActualizarTarifaRequest request, Authentication auth) {
        return zonaDomicilioService.actualizarTarifa(id, request, auth);
    }
}
