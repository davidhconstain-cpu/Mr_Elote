package com.mrelote.pedidos.controller;

import com.mrelote.pedidos.dto.request.ComboComponenteRequest;
import com.mrelote.pedidos.dto.request.ComboRequest;
import com.mrelote.pedidos.dto.response.ComboResponse;
import com.mrelote.pedidos.service.ComboService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/combos")
@RequiredArgsConstructor
public class ComboController {

    private final ComboService comboService;

    @GetMapping
    public Page<ComboResponse> listar(Pageable pageable) {
        return comboService.listar(pageable);
    }

    @GetMapping("/{id}")
    public ComboResponse ver(@PathVariable Long id) {
        return comboService.ver(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('Administrador')")
    public ResponseEntity<ComboResponse> crear(@Valid @RequestBody ComboRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(comboService.crear(request));
    }

    @PutMapping("/{id}/componentes")
    @PreAuthorize("hasRole('Administrador')")
    public ComboResponse definirComponentes(@PathVariable Long id, @Valid @RequestBody List<ComboComponenteRequest> componentes) {
        return comboService.definirComponentes(id, componentes);
    }
}
