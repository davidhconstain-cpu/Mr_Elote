package com.mrelote.pedidos.controller;

import com.mrelote.pedidos.dto.request.AccionPedidoRequest;
import com.mrelote.pedidos.dto.request.CambiarItemsPedidoRequest;
import com.mrelote.pedidos.dto.request.CrearPedidoRequest;
import com.mrelote.pedidos.dto.response.HistorialPedidoResponse;
import com.mrelote.pedidos.dto.response.PedidoResponse;
import com.mrelote.pedidos.service.PedidoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/pedidos")
@RequiredArgsConstructor
public class PedidoController {

    private final PedidoService pedidoService;

    /** RF-005 a RF-007: autenticación opcional, ver SecurityConfig#PUBLIC_ANY_METHOD. */
    @PostMapping
    public ResponseEntity<PedidoResponse> crear(@Valid @RequestBody CrearPedidoRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pedidoService.crear(request, auth));
    }

    @GetMapping
    @PreAuthorize("hasRole('Caja') or hasRole('Administrador') or hasRole('Mesero')")
    public Page<PedidoResponse> buscar(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            Pageable pageable) {
        return pedidoService.buscar(estado, tipo, desde, hasta, pageable);
    }

    @GetMapping("/{id}")
    public PedidoResponse ver(@PathVariable Long id) {
        return pedidoService.ver(id);
    }

    @PatchMapping("/{id}/items")
    @PreAuthorize("hasRole('Mesero') or hasRole('Caja') or hasRole('Administrador')")
    public PedidoResponse modificarItems(@PathVariable Long id, @Valid @RequestBody CambiarItemsPedidoRequest request, Authentication auth) {
        return pedidoService.modificarItems(id, request, auth);
    }

    @GetMapping("/{id}/historial")
    public List<HistorialPedidoResponse> historial(@PathVariable Long id) {
        return pedidoService.historial(id);
    }

    @PostMapping("/{id}/confirmar-pago")
    @PreAuthorize("hasRole('Caja')")
    public PedidoResponse confirmarPago(@PathVariable Long id, @RequestBody(required = false) AccionPedidoRequest body, Authentication auth) {
        return pedidoService.confirmarPago(id, motivo(body), auth);
    }

    @PostMapping("/{id}/iniciar-preparacion")
    @PreAuthorize("hasRole('Cocina')")
    public PedidoResponse iniciarPreparacion(@PathVariable Long id, @RequestBody(required = false) AccionPedidoRequest body, Authentication auth) {
        return pedidoService.iniciarPreparacion(id, motivo(body), auth);
    }

    @PostMapping("/{id}/marcar-listo")
    @PreAuthorize("hasRole('Cocina')")
    public PedidoResponse marcarListo(@PathVariable Long id, @RequestBody(required = false) AccionPedidoRequest body, Authentication auth) {
        return pedidoService.marcarListo(id, motivo(body), auth);
    }

    @PostMapping("/{id}/entregar")
    @PreAuthorize("hasRole('Mesero') or hasRole('Caja')")
    public PedidoResponse entregar(@PathVariable Long id, @RequestBody(required = false) AccionPedidoRequest body, Authentication auth) {
        return pedidoService.entregar(id, motivo(body), auth);
    }

    @PostMapping("/{id}/recoger")
    @PreAuthorize("hasRole('Caja')")
    public PedidoResponse recoger(@PathVariable Long id, @RequestBody(required = false) AccionPedidoRequest body, Authentication auth) {
        return pedidoService.recoger(id, motivo(body), auth);
    }

    @PostMapping("/{id}/despachar")
    @PreAuthorize("hasRole('Despachos') or hasRole('Administrador')")
    public PedidoResponse despachar(@PathVariable Long id, @RequestBody(required = false) AccionPedidoRequest body, Authentication auth) {
        return pedidoService.despachar(id, motivo(body), auth);
    }

    @PostMapping("/{id}/anular")
    @PreAuthorize("hasRole('Administrador') or hasRole('Caja')")
    public PedidoResponse anular(@PathVariable Long id, @RequestBody AccionPedidoRequest body, Authentication auth) {
        return pedidoService.anular(id, motivo(body), auth);
    }

    private String motivo(AccionPedidoRequest body) {
        return body != null ? body.motivo() : null;
    }
}
