package com.mrelote.pedidos.controller;

import com.mrelote.pedidos.dto.response.ProductoVendidoResponse;
import com.mrelote.pedidos.dto.response.ResumenVentasResponse;
import com.mrelote.pedidos.service.InformeService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('Administrador')")
public class InformeController {

    private final InformeService informeService;

    @GetMapping("/api/v1/informes/ventas")
    public List<ResumenVentasResponse> ventas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String agrupar) {
        return informeService.ventas(desde, hasta, agrupar);
    }

    @GetMapping("/api/v1/informes/productos-mas-vendidos")
    public List<ProductoVendidoResponse> productosMasVendidos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return informeService.productosMasVendidos(desde, hasta);
    }

    @GetMapping("/api/v1/dashboard/resumen")
    public ResumenVentasResponse dashboardResumen() {
        return informeService.resumenDashboard();
    }
}
