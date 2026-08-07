package com.mrelote.pedidos.controller;

import com.mrelote.pedidos.dto.request.DisponibilidadRequest;
import com.mrelote.pedidos.dto.request.ProductoRequest;
import com.mrelote.pedidos.dto.response.ImagenProductoResponse;
import com.mrelote.pedidos.dto.response.ProductoResponse;
import com.mrelote.pedidos.entity.Categoria;
import com.mrelote.pedidos.entity.Producto;
import com.mrelote.pedidos.exception.NotFoundException;
import com.mrelote.pedidos.repository.CategoriaRepository;
import com.mrelote.pedidos.repository.ProductoRepository;
import com.mrelote.pedidos.service.AuditoriaService;
import com.mrelote.pedidos.service.ImagenProductoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** RF-001, RF-025, RF-028, RF-029: catálogo de lectura pública; escritura solo Administrador. */
@RestController
@RequestMapping("/api/v1/productos")
@RequiredArgsConstructor
public class ProductoController {

    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;
    private final AuditoriaService auditoriaService;
    private final ImagenProductoService imagenProductoService;

    @GetMapping
    public Page<ProductoResponse> listar(
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) Boolean disponible,
            Pageable pageable) {
        Page<Producto> page;
        if (categoriaId != null && disponible != null) {
            page = productoRepository.findByCategoriaIdAndDisponible(categoriaId, disponible, pageable);
        } else if (categoriaId != null) {
            page = productoRepository.findByCategoriaId(categoriaId, pageable);
        } else if (disponible != null) {
            page = productoRepository.findByDisponible(disponible, pageable);
        } else {
            page = productoRepository.findAll(pageable);
        }
        return page.map(ProductoResponse::from);
    }

    @GetMapping("/{id}")
    public ProductoResponse ver(@PathVariable Long id) {
        return ProductoResponse.from(buscar(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('Administrador')")
    public ResponseEntity<ProductoResponse> crear(@Valid @RequestBody ProductoRequest request, Authentication auth) {
        Categoria categoria = categoriaRepository.findById(request.categoriaId())
                .orElseThrow(() -> new NotFoundException("No existe la categoría " + request.categoriaId()));
        Producto producto = Producto.builder()
                .categoria(categoria)
                .codigo(request.codigo())
                .nombre(request.nombre())
                .descripcion(request.descripcion())
                .precio(request.precio())
                .disponible(request.disponible() != null ? request.disponible() : true)
                .activo(request.activo() != null ? request.activo() : true)
                .build();
        producto = productoRepository.save(producto);
        auditoriaService.registrar(auth, "crear", "producto", producto.getId(), null, ProductoResponse.from(producto));
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductoResponse.from(producto));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('Administrador')")
    public ProductoResponse editar(@PathVariable Long id, @RequestBody ProductoRequest request, Authentication auth) {
        Producto producto = buscar(id);
        ProductoResponse anterior = ProductoResponse.from(producto);
        if (request.categoriaId() != null) {
            producto.setCategoria(categoriaRepository.findById(request.categoriaId())
                    .orElseThrow(() -> new NotFoundException("No existe la categoría " + request.categoriaId())));
        }
        if (request.codigo() != null) producto.setCodigo(request.codigo());
        if (request.nombre() != null) producto.setNombre(request.nombre());
        if (request.descripcion() != null) producto.setDescripcion(request.descripcion());
        if (request.precio() != null) producto.setPrecio(request.precio());
        if (request.activo() != null) producto.setActivo(request.activo());
        producto = productoRepository.save(producto);
        auditoriaService.registrar(auth, "editar", "producto", id, anterior, ProductoResponse.from(producto));
        return ProductoResponse.from(producto);
    }

    /** RF-028, RF-029, RN-004: acción operativa frecuente, separada de editar todo el producto. */
    @PatchMapping("/{id}/disponibilidad")
    @PreAuthorize("hasRole('Administrador') or hasRole('Cocina')")
    public ProductoResponse cambiarDisponibilidad(@PathVariable Long id, @Valid @RequestBody DisponibilidadRequest request, Authentication auth) {
        Producto producto = buscar(id);
        boolean anterior = Boolean.TRUE.equals(producto.getDisponible());
        producto.setDisponible(request.disponible());
        producto = productoRepository.save(producto);
        auditoriaService.registrar(auth, "cambiar_disponibilidad", "producto", id, anterior, request.disponible());
        return ProductoResponse.from(producto);
    }

    @PostMapping("/{id}/imagenes")
    @PreAuthorize("hasRole('Administrador')")
    public ResponseEntity<ImagenProductoResponse> agregarImagen(@PathVariable Long id, @RequestParam("archivo") MultipartFile archivo) {
        return ResponseEntity.status(HttpStatus.CREATED).body(imagenProductoService.agregar(id, archivo));
    }

    private Producto buscar(Long id) {
        return productoRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("No existe el producto " + id));
    }
}
