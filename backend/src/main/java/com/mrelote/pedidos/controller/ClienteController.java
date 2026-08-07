package com.mrelote.pedidos.controller;

import com.mrelote.pedidos.dto.request.DireccionRequest;
import com.mrelote.pedidos.dto.response.DireccionResponse;
import com.mrelote.pedidos.dto.response.PedidoResponse;
import com.mrelote.pedidos.exception.BusinessRuleException;
import com.mrelote.pedidos.repository.PedidoRepository;
import com.mrelote.pedidos.security.UsuarioPrincipal;
import com.mrelote.pedidos.service.DireccionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** RF-002, RF-040: perfil, direcciones e historial del cliente autenticado. */
@RestController
@RequestMapping("/api/v1/clientes/me")
@RequiredArgsConstructor
@PreAuthorize("hasRole('Cliente')")
public class ClienteController {

    private final DireccionService direccionService;
    private final PedidoRepository pedidoRepository;

    @GetMapping("/direcciones")
    public List<DireccionResponse> misDirecciones(Authentication auth) {
        return direccionService.listarMias(id(auth));
    }

    @PostMapping("/direcciones")
    public ResponseEntity<DireccionResponse> crearDireccion(@RequestBody DireccionRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(direccionService.crear(id(auth), request));
    }

    @PatchMapping("/direcciones/{id}")
    public DireccionResponse editarDireccion(@PathVariable Long id, @RequestBody DireccionRequest request, Authentication auth) {
        return direccionService.editar(id(auth), id, request);
    }

    @DeleteMapping("/direcciones/{id}")
    public ResponseEntity<Void> eliminarDireccion(@PathVariable Long id, Authentication auth) {
        direccionService.eliminar(id(auth), id);
        return ResponseEntity.noContent().build();
    }

    /** @Transactional: pedido.items es LAZY y open-in-view está deshabilitado (application.yml). */
    @GetMapping("/pedidos")
    @Transactional(readOnly = true)
    public Page<PedidoResponse> misPedidos(Authentication auth, Pageable pageable) {
        return pedidoRepository.findByClienteUsuarioIdOrderByCreadoEnDesc(id(auth), pageable).map(PedidoResponse::from);
    }

    private Long id(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof UsuarioPrincipal p)) {
            throw new BusinessRuleException("Se requiere un cliente autenticado");
        }
        return p.id();
    }
}
