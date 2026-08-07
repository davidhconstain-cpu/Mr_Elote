package com.mrelote.pedidos.controller;

import com.mrelote.pedidos.dto.request.AdicionalRequest;
import com.mrelote.pedidos.dto.response.AdicionalResponse;
import com.mrelote.pedidos.entity.Adicional;
import com.mrelote.pedidos.repository.AdicionalRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** RF-026. */
@RestController
@RequestMapping("/api/v1/adicionales")
@RequiredArgsConstructor
public class AdicionalController {

    private final AdicionalRepository adicionalRepository;

    @GetMapping
    public List<AdicionalResponse> listar() {
        return adicionalRepository.findAll().stream().map(AdicionalResponse::from).toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('Administrador')")
    public ResponseEntity<AdicionalResponse> crear(@Valid @RequestBody AdicionalRequest request) {
        Adicional adicional = Adicional.builder()
                .nombre(request.nombre())
                .precio(request.precio())
                .disponible(request.disponible() != null ? request.disponible() : true)
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(AdicionalResponse.from(adicionalRepository.save(adicional)));
    }
}
