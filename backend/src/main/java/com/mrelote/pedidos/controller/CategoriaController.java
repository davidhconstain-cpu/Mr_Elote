package com.mrelote.pedidos.controller;

import com.mrelote.pedidos.dto.request.CategoriaRequest;
import com.mrelote.pedidos.dto.response.CategoriaResponse;
import com.mrelote.pedidos.entity.Categoria;
import com.mrelote.pedidos.exception.NotFoundException;
import com.mrelote.pedidos.repository.CategoriaRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** RF-001, RF-025: catálogo visible sin autenticación (ver SecurityConfig#PUBLIC_GET). */
@RestController
@RequestMapping("/api/v1/categorias")
@RequiredArgsConstructor
public class CategoriaController {

    private final CategoriaRepository categoriaRepository;

    @GetMapping
    public List<CategoriaResponse> listar() {
        return categoriaRepository.findAll().stream().map(CategoriaResponse::from).toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('Administrador')")
    public ResponseEntity<CategoriaResponse> crear(@Valid @RequestBody CategoriaRequest request) {
        Categoria categoria = Categoria.builder()
                .nombre(request.nombre())
                .orden(request.orden() != null ? request.orden() : 0)
                .activa(request.activa() != null ? request.activa() : true)
                .build();
        categoria = categoriaRepository.save(categoria);
        return ResponseEntity.status(HttpStatus.CREATED).body(CategoriaResponse.from(categoria));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('Administrador')")
    public CategoriaResponse editar(@PathVariable Long id, @RequestBody CategoriaRequest request) {
        Categoria categoria = categoriaRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("No existe la categoría " + id));
        if (request.nombre() != null) categoria.setNombre(request.nombre());
        if (request.orden() != null) categoria.setOrden(request.orden());
        if (request.activa() != null) categoria.setActiva(request.activa());
        return CategoriaResponse.from(categoriaRepository.save(categoria));
    }
}
