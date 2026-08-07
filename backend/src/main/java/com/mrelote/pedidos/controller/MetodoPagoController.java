package com.mrelote.pedidos.controller;

import com.mrelote.pedidos.dto.response.MetodoPagoResponse;
import com.mrelote.pedidos.entity.MetodoPago;
import com.mrelote.pedidos.repository.MetodoPagoRepository;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/metodos-pago")
@RequiredArgsConstructor
public class MetodoPagoController {

    private final MetodoPagoRepository metodoPagoRepository;

    public record MetodoPagoRequest(@NotBlank String nombre, Boolean activo) {
    }

    @GetMapping
    public List<MetodoPagoResponse> listar() {
        return metodoPagoRepository.findAll().stream().map(MetodoPagoResponse::from).toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('Administrador')")
    public ResponseEntity<MetodoPagoResponse> crear(@RequestBody MetodoPagoRequest request) {
        MetodoPago metodo = MetodoPago.builder()
                .nombre(request.nombre())
                .activo(request.activo() != null ? request.activo() : true)
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(MetodoPagoResponse.from(metodoPagoRepository.save(metodo)));
    }
}
