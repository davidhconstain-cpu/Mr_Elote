package com.mrelote.pedidos.service;

import com.mrelote.pedidos.dto.response.DespachoResponse;
import com.mrelote.pedidos.entity.Despacho;
import com.mrelote.pedidos.exception.ConflictException;
import com.mrelote.pedidos.exception.NotFoundException;
import com.mrelote.pedidos.repository.DespachoRepository;
import com.mrelote.pedidos.repository.UsuarioRepository;
import com.mrelote.pedidos.security.UsuarioPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/** RF-033, sección 18: gestión de pedidos de domicilio y su entrega. */
@Service
@RequiredArgsConstructor
public class DespachoService {

    private final DespachoRepository despachoRepository;
    private final UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public List<DespachoResponse> listar() {
        return despachoRepository.findByEstadoInOrderByAsignadoEnAsc(List.of("asignado", "en_camino")).stream()
                .map(DespachoResponse::from).toList();
    }

    @Transactional
    public DespachoResponse asignar(Long id, Long domiciliarioId, Authentication auth) {
        Despacho despacho = buscar(id);
        if (domiciliarioId != null) {
            despacho.setDomiciliario(usuarioRepository.findById(domiciliarioId)
                    .orElseThrow(() -> new NotFoundException("No existe el usuario " + domiciliarioId)));
        } else if (auth != null && auth.getPrincipal() instanceof UsuarioPrincipal principal) {
            despacho.setDomiciliario(principal.usuario());
        }
        return DespachoResponse.from(despachoRepository.save(despacho));
    }

    @Transactional
    public DespachoResponse marcarEnCamino(Long id) {
        Despacho despacho = buscar(id);
        exigirOrigen(despacho, "asignado");
        despacho.setEstado("en_camino");
        return DespachoResponse.from(despachoRepository.save(despacho));
    }

    @Transactional
    public DespachoResponse marcarEntregado(Long id) {
        Despacho despacho = buscar(id);
        exigirOrigen(despacho, "en_camino", "asignado");
        despacho.setEstado("entregado");
        despacho.setEntregadoEn(OffsetDateTime.now());
        return DespachoResponse.from(despachoRepository.save(despacho));
    }

    private void exigirOrigen(Despacho despacho, String... estadosValidos) {
        for (String estado : estadosValidos) {
            if (estado.equals(despacho.getEstado())) return;
        }
        throw new ConflictException("El despacho está en estado '" + despacho.getEstado() + "'");
    }

    private Despacho buscar(Long id) {
        return despachoRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("No existe el despacho " + id));
    }
}
