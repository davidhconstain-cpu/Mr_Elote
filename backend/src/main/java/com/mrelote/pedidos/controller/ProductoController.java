package com.mrelote.pedidos.controller;

import com.mrelote.pedidos.dto.request.AsignarAdicionalRequest;
import com.mrelote.pedidos.dto.request.AsignarOpcionRequest;
import com.mrelote.pedidos.dto.request.DisponibilidadRequest;
import com.mrelote.pedidos.dto.request.ProductoRequest;
import com.mrelote.pedidos.dto.response.ImagenProductoResponse;
import com.mrelote.pedidos.dto.response.ProductoOpcionResponse;
import com.mrelote.pedidos.dto.response.ProductoResponse;
import com.mrelote.pedidos.entity.Adicional;
import com.mrelote.pedidos.entity.Categoria;
import com.mrelote.pedidos.entity.Opcion;
import com.mrelote.pedidos.entity.Producto;
import com.mrelote.pedidos.entity.ProductoOpcion;
import com.mrelote.pedidos.exception.NotFoundException;
import com.mrelote.pedidos.repository.AdicionalRepository;
import com.mrelote.pedidos.repository.CategoriaRepository;
import com.mrelote.pedidos.repository.OpcionRepository;
import com.mrelote.pedidos.repository.ProductoOpcionRepository;
import com.mrelote.pedidos.repository.ProductoRepository;
import com.mrelote.pedidos.repository.ValorOpcionRepository;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** RF-001, RF-025, RF-028, RF-029: catálogo de lectura pública; escritura solo Administrador. */
@RestController
@RequestMapping("/api/v1/productos")
@RequiredArgsConstructor
public class ProductoController {

    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;
    private final AuditoriaService auditoriaService;
    private final ImagenProductoService imagenProductoService;
    private final ProductoOpcionRepository productoOpcionRepository;
    private final OpcionRepository opcionRepository;
    private final ValorOpcionRepository valorOpcionRepository;
    private final AdicionalRepository adicionalRepository;

    /** @Transactional: producto.imagenes es LAZY y open-in-view está deshabilitado (application.yml). */
    @GetMapping
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
    public ProductoResponse ver(@PathVariable Long id) {
        return ProductoResponse.from(buscar(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('Administrador')")
    @Transactional
    public ResponseEntity<ProductoResponse> crear(@Valid @RequestBody ProductoRequest request, Authentication auth) {
        Categoria categoria = categoriaRepository.findById(request.categoriaId())
                .orElseThrow(() -> new NotFoundException("No existe la categoría " + request.categoriaId()));
        Producto producto = Producto.builder()
                .categoria(categoria)
                .codigo(request.codigo())
                .nombre(request.nombre())
                .descripcion(request.descripcion())
                .precio(request.precio())
                .porcionPersonas(request.porcionPersonas())
                .disponible(request.disponible() != null ? request.disponible() : true)
                .activo(request.activo() != null ? request.activo() : true)
                .build();
        producto = productoRepository.save(producto);
        auditoriaService.registrar(auth, "crear", "producto", producto.getId(), null, ProductoResponse.from(producto));
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductoResponse.from(producto));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('Administrador')")
    @Transactional
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
        if (request.porcionPersonas() != null) producto.setPorcionPersonas(request.porcionPersonas());
        if (request.activo() != null) producto.setActivo(request.activo());
        producto = productoRepository.save(producto);
        auditoriaService.registrar(auth, "editar", "producto", id, anterior, ProductoResponse.from(producto));
        return ProductoResponse.from(producto);
    }

    /** RF-028, RF-029, RN-004: acción operativa frecuente, separada de editar todo el producto. */
    @PatchMapping("/{id}/disponibilidad")
    @PreAuthorize("hasRole('Administrador') or hasRole('Cocina')")
    @Transactional
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

    /** RF-026: opciones (ej. tamaño) que aplican a este producto, con sus valores y si es obligatoria. */
    @GetMapping("/{id}/opciones")
    @Transactional(readOnly = true)
    public List<ProductoOpcionResponse> listarOpciones(@PathVariable Long id) {
        return productoOpcionRepository.findByProductoId(id).stream()
                .map(po -> ProductoOpcionResponse.from(po, valorOpcionRepository.findByOpcionId(po.getOpcion().getId())))
                .toList();
    }

    @PostMapping("/{id}/opciones")
    @PreAuthorize("hasRole('Administrador')")
    @Transactional
    public ResponseEntity<List<ProductoOpcionResponse>> asignarOpcion(@PathVariable Long id, @Valid @RequestBody AsignarOpcionRequest request) {
        Producto producto = buscar(id);
        Opcion opcion = opcionRepository.findById(request.opcionId())
                .orElseThrow(() -> new NotFoundException("No existe la opción " + request.opcionId()));
        productoOpcionRepository.save(ProductoOpcion.builder()
                .producto(producto)
                .opcion(opcion)
                .obligatoria(request.obligatoria() != null ? request.obligatoria() : false)
                .build());
        return ResponseEntity.status(HttpStatus.CREATED).body(listarOpciones(id));
    }

    @DeleteMapping("/{id}/opciones/{opcionId}")
    @PreAuthorize("hasRole('Administrador')")
    @Transactional
    public ResponseEntity<Void> quitarOpcion(@PathVariable Long id, @PathVariable Long opcionId) {
        productoOpcionRepository.findByProductoId(id).stream()
                .filter(po -> po.getOpcion().getId().equals(opcionId))
                .findFirst()
                .ifPresent(productoOpcionRepository::delete);
        return ResponseEntity.noContent().build();
    }

    /** RF-026: adicionales (ej. queso extra) ofrecibles con este producto. */
    @PostMapping("/{id}/adicionales")
    @PreAuthorize("hasRole('Administrador')")
    @Transactional
    public ProductoResponse asignarAdicional(@PathVariable Long id, @Valid @RequestBody AsignarAdicionalRequest request) {
        Producto producto = buscar(id);
        Adicional adicional = adicionalRepository.findById(request.adicionalId())
                .orElseThrow(() -> new NotFoundException("No existe el adicional " + request.adicionalId()));
        producto.getAdicionales().add(adicional);
        return ProductoResponse.from(productoRepository.save(producto));
    }

    @DeleteMapping("/{id}/adicionales/{adicionalId}")
    @PreAuthorize("hasRole('Administrador')")
    @Transactional
    public ProductoResponse quitarAdicional(@PathVariable Long id, @PathVariable Long adicionalId) {
        Producto producto = buscar(id);
        producto.getAdicionales().removeIf(a -> a.getId().equals(adicionalId));
        return ProductoResponse.from(productoRepository.save(producto));
    }

    private Producto buscar(Long id) {
        return productoRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("No existe el producto " + id));
    }
}
