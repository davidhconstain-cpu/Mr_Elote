package com.mrelote.pedidos.service;

import com.mrelote.pedidos.dto.request.ComboComponenteRequest;
import com.mrelote.pedidos.dto.request.ComboRequest;
import com.mrelote.pedidos.dto.response.ComboResponse;
import com.mrelote.pedidos.entity.Combo;
import com.mrelote.pedidos.entity.ComboDetalle;
import com.mrelote.pedidos.entity.Producto;
import com.mrelote.pedidos.exception.NotFoundException;
import com.mrelote.pedidos.repository.ComboDetalleRepository;
import com.mrelote.pedidos.repository.ComboDisponibilidadRepository;
import com.mrelote.pedidos.repository.ComboRepository;
import com.mrelote.pedidos.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Disponibilidad de combos (sección 11 v1.1): se calcula con la vista SQL
 * combo_disponibilidad, no en memoria, para que quede consistente con
 * cualquier otro consumidor directo de la base de datos.
 */
@Service
@RequiredArgsConstructor
public class ComboService {

    private final ComboRepository comboRepository;
    private final ComboDetalleRepository comboDetalleRepository;
    private final ComboDisponibilidadRepository comboDisponibilidadRepository;
    private final ProductoRepository productoRepository;

    @Transactional(readOnly = true)
    public Page<ComboResponse> listar(Pageable pageable) {
        Page<Combo> page = comboRepository.findAll(pageable);
        Map<Long, Boolean> disponibilidad = comboDisponibilidadRepository
                .findByComboIdIn(page.getContent().stream().map(Combo::getId).toList())
                .stream()
                .collect(Collectors.toMap(d -> d.getComboId(), d -> Boolean.TRUE.equals(d.getDisponible())));
        return page.map(c -> ComboResponse.from(c, disponibilidad.getOrDefault(c.getId(), false)));
    }

    @Transactional(readOnly = true)
    public ComboResponse ver(Long id) {
        Combo combo = buscar(id);
        boolean disponible = comboDisponibilidadRepository.findByComboId(id)
                .map(d -> Boolean.TRUE.equals(d.getDisponible()))
                .orElse(false);
        return ComboResponse.from(combo, disponible);
    }

    @Transactional
    public ComboResponse crear(ComboRequest request) {
        Combo combo = Combo.builder()
                .nombre(request.nombre())
                .descripcion(request.descripcion())
                .precio(request.precio())
                .activo(request.activo() != null ? request.activo() : true)
                .build();
        combo = comboRepository.save(combo);
        // Un combo recién creado, sin componentes, no aparece en la vista
        // combo_disponibilidad (no hay filas que agrupar) -> se trata como
        // no disponible hasta que se le agreguen componentes.
        return ComboResponse.from(combo, false);
    }

    @Transactional
    public ComboResponse definirComponentes(Long comboId, List<ComboComponenteRequest> componentes) {
        Combo combo = buscar(comboId);
        comboDetalleRepository.deleteByComboId(comboId);
        comboDetalleRepository.flush();

        for (ComboComponenteRequest c : componentes) {
            Producto producto = productoRepository.findById(c.productoId())
                    .orElseThrow(() -> new NotFoundException("No existe el producto " + c.productoId()));
            ComboDetalle detalle = ComboDetalle.builder()
                    .combo(combo)
                    .producto(producto)
                    .grupo(c.grupo())
                    .cantidad(c.cantidad() != null ? c.cantidad() : 1)
                    .permiteExtras(c.permiteExtras() != null ? c.permiteExtras() : false)
                    .build();
            comboDetalleRepository.save(detalle);
        }
        return ver(comboId);
    }

    private Combo buscar(Long id) {
        return comboRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("No existe el combo " + id));
    }
}
