package com.mrelote.pedidos.controller;

import com.mrelote.pedidos.dto.request.AbrirCajaRequest;
import com.mrelote.pedidos.dto.request.CerrarCajaRequest;
import com.mrelote.pedidos.dto.request.MovimientoCajaRequest;
import com.mrelote.pedidos.dto.response.CajaResponse;
import com.mrelote.pedidos.dto.response.MovimientoCajaResponse;
import com.mrelote.pedidos.service.CajaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/caja")
@RequiredArgsConstructor
@PreAuthorize("hasRole('Caja') or hasRole('Administrador')")
public class CajaController {

    private final CajaService cajaService;

    @PostMapping("/apertura")
    public ResponseEntity<CajaResponse> abrir(@Valid @RequestBody AbrirCajaRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cajaService.abrir(request, auth));
    }

    @GetMapping("/actual")
    public CajaResponse actual() {
        return cajaService.actual();
    }

    @GetMapping("/{id}/movimientos")
    public List<MovimientoCajaResponse> movimientos(@PathVariable Long id) {
        return cajaService.movimientos(id);
    }

    @PostMapping("/{id}/movimientos")
    public ResponseEntity<MovimientoCajaResponse> registrarMovimiento(
            @PathVariable Long id, @Valid @RequestBody MovimientoCajaRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cajaService.registrarMovimiento(id, request, auth));
    }

    @PostMapping("/{id}/cierre")
    public CajaResponse cerrar(@PathVariable Long id, @Valid @RequestBody CerrarCajaRequest request, Authentication auth) {
        return cajaService.cerrar(id, request, auth);
    }
}
