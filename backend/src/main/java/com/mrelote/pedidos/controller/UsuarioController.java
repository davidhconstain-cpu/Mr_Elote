package com.mrelote.pedidos.controller;

import com.mrelote.pedidos.dto.request.CrearUsuarioRequest;
import com.mrelote.pedidos.dto.request.EditarUsuarioRequest;
import com.mrelote.pedidos.dto.response.UsuarioResponse;
import com.mrelote.pedidos.exception.BusinessRuleException;
import com.mrelote.pedidos.security.UsuarioPrincipal;
import com.mrelote.pedidos.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    @GetMapping("/me")
    public UsuarioResponse miPerfil(Authentication auth) {
        return UsuarioResponse.from(principal(auth).usuario());
    }

    @PatchMapping("/me")
    public UsuarioResponse editarMiPerfil(@RequestBody EditarUsuarioRequest request, Authentication auth) {
        // Un usuario no puede autoasignarse un rol distinto.
        EditarUsuarioRequest sinRol = new EditarUsuarioRequest(request.nombre(), request.telefono(), null, null);
        return usuarioService.editar(principal(auth).id(), sinRol, auth);
    }

    @GetMapping
    @PreAuthorize("hasRole('Administrador')")
    public Page<UsuarioResponse> listar(@RequestParam(required = false) Long rolId, Pageable pageable) {
        return usuarioService.listar(rolId, pageable);
    }

    @PostMapping
    @PreAuthorize("hasRole('Administrador')")
    public ResponseEntity<UsuarioResponse> crear(@Valid @RequestBody CrearUsuarioRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(usuarioService.crear(request, auth));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('Administrador')")
    public UsuarioResponse ver(@PathVariable Long id) {
        return usuarioService.ver(id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('Administrador')")
    public UsuarioResponse editar(@PathVariable Long id, @RequestBody EditarUsuarioRequest request, Authentication auth) {
        return usuarioService.editar(id, request, auth);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('Administrador')")
    public ResponseEntity<Void> desactivar(@PathVariable Long id, Authentication auth) {
        usuarioService.desactivar(id, auth);
        return ResponseEntity.noContent().build();
    }

    private UsuarioPrincipal principal(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof UsuarioPrincipal p)) {
            throw new BusinessRuleException("Se requiere un usuario autenticado");
        }
        return p;
    }
}
