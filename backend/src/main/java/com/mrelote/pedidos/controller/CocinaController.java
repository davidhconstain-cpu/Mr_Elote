package com.mrelote.pedidos.controller;

import com.mrelote.pedidos.dto.response.PedidoResponse;
import com.mrelote.pedidos.service.PedidoService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** RF-018 a RF-021. */
@RestController
@RequestMapping("/api/v1/cocina")
@RequiredArgsConstructor
@PreAuthorize("hasRole('Cocina')")
public class CocinaController {

    private final PedidoService pedidoService;

    @GetMapping("/pedidos")
    public List<PedidoResponse> cola(
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String estado) {
        return pedidoService.colaCocina(tipo, estado);
    }
}
