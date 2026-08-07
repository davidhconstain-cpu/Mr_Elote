package com.mrelote.pedidos.controller;

import com.mrelote.pedidos.dto.request.AccionPedidoRequest;
import com.mrelote.pedidos.dto.request.RegistrarPagoRequest;
import com.mrelote.pedidos.dto.request.WebhookPagoRequest;
import com.mrelote.pedidos.dto.response.PagoResponse;
import com.mrelote.pedidos.service.PagoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class PagoController {

    private final PagoService pagoService;

    @PostMapping("/api/v1/pedidos/{id}/pagos")
    @PreAuthorize("hasRole('Cliente') or hasRole('Mesero') or hasRole('Caja')")
    public ResponseEntity<PagoResponse> registrar(@PathVariable Long id, @Valid @RequestBody RegistrarPagoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pagoService.registrar(id, request));
    }

    /** RF-016, RNF-007, RNF-008: público pero firmado (ver SecurityConfig#PUBLIC_ANY_METHOD). */
    @PostMapping("/api/v1/pagos/webhook/{proveedor}")
    public ResponseEntity<Void> webhook(@PathVariable String proveedor, @Valid @RequestBody WebhookPagoRequest request) {
        pagoService.procesarWebhook(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/v1/pagos/{id}/validar")
    @PreAuthorize("hasRole('Caja')")
    public PagoResponse validar(@PathVariable Long id, Authentication auth) {
        return pagoService.validar(id, auth);
    }

    @PostMapping("/api/v1/pagos/{id}/rechazar")
    @PreAuthorize("hasRole('Caja')")
    public PagoResponse rechazar(@PathVariable Long id, @RequestBody(required = false) AccionPedidoRequest body) {
        return pagoService.rechazar(id, body);
    }
}
