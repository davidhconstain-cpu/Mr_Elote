package com.mrelote.pedidos.controller;

import com.mrelote.pedidos.dto.request.OpcionRequest;
import com.mrelote.pedidos.dto.request.ValorOpcionRequest;
import com.mrelote.pedidos.dto.response.OpcionResponse;
import com.mrelote.pedidos.entity.Opcion;
import com.mrelote.pedidos.entity.ValorOpcion;
import com.mrelote.pedidos.exception.NotFoundException;
import com.mrelote.pedidos.repository.OpcionRepository;
import com.mrelote.pedidos.repository.ValorOpcionRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/** RF-026. */
@RestController
@RequestMapping("/api/v1/opciones")
@RequiredArgsConstructor
public class OpcionController {

    private final OpcionRepository opcionRepository;
    private final ValorOpcionRepository valorOpcionRepository;

    @GetMapping
    public List<OpcionResponse> listar() {
        return opcionRepository.findAll().stream()
                .map(o -> OpcionResponse.from(o, valorOpcionRepository.findByOpcionId(o.getId())))
                .toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('Administrador')")
    public ResponseEntity<OpcionResponse> crear(@Valid @RequestBody OpcionRequest request) {
        Opcion opcion = Opcion.builder().nombre(request.nombre()).tipo(request.tipo()).build();
        opcion = opcionRepository.save(opcion);
        return ResponseEntity.status(HttpStatus.CREATED).body(OpcionResponse.from(opcion, List.of()));
    }

    @PostMapping("/{id}/valores")
    @PreAuthorize("hasRole('Administrador')")
    public ResponseEntity<OpcionResponse> agregarValor(@PathVariable Long id, @Valid @RequestBody ValorOpcionRequest request) {
        Opcion opcion = opcionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("No existe la opción " + id));
        ValorOpcion valor = ValorOpcion.builder()
                .opcion(opcion)
                .nombre(request.nombre())
                .precioAdicional(request.precioAdicional() != null ? request.precioAdicional() : BigDecimal.ZERO)
                .build();
        valorOpcionRepository.save(valor);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(OpcionResponse.from(opcion, valorOpcionRepository.findByOpcionId(id)));
    }
}
