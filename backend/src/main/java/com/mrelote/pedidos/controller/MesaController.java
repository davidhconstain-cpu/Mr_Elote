package com.mrelote.pedidos.controller;

import com.mrelote.pedidos.dto.request.MesaRequest;
import com.mrelote.pedidos.dto.response.MesaResponse;
import com.mrelote.pedidos.dto.response.QrMesaResponse;
import com.mrelote.pedidos.service.MesaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/mesas")
@RequiredArgsConstructor
public class MesaController {

    private final MesaService mesaService;

    @GetMapping
    @PreAuthorize("hasRole('Mesero') or hasRole('Administrador')")
    public List<MesaResponse> listar() {
        return mesaService.listar();
    }

    @PostMapping
    @PreAuthorize("hasRole('Administrador')")
    public ResponseEntity<MesaResponse> crear(@Valid @RequestBody MesaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mesaService.crear(request));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('Administrador')")
    public MesaResponse editar(@PathVariable Long id, @RequestBody MesaRequest request) {
        return mesaService.editar(id, request);
    }

    @PostMapping("/{id}/qr/regenerar")
    @PreAuthorize("hasRole('Administrador')")
    public ResponseEntity<QrMesaResponse> regenerarQr(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mesaService.regenerarQr(id));
    }

    /** RF-004: identifica la mesa a partir del QR escaneado, sin login (ver SecurityConfig#PUBLIC_GET). */
    @GetMapping("/qr/{codigo}")
    public MesaResponse resolverQr(@PathVariable String codigo) {
        return mesaService.resolverQr(codigo);
    }
}
