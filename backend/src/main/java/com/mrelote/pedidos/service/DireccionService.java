package com.mrelote.pedidos.service;

import com.mrelote.pedidos.dto.request.DireccionRequest;
import com.mrelote.pedidos.dto.response.DireccionResponse;
import com.mrelote.pedidos.entity.Direccion;
import com.mrelote.pedidos.entity.ZonaDomicilio;
import com.mrelote.pedidos.exception.BusinessRuleException;
import com.mrelote.pedidos.exception.NotFoundException;
import com.mrelote.pedidos.repository.ClienteRepository;
import com.mrelote.pedidos.repository.DireccionRepository;
import com.mrelote.pedidos.repository.ZonaDomicilioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DireccionService {

    private final DireccionRepository direccionRepository;
    private final ClienteRepository clienteRepository;
    private final ZonaDomicilioRepository zonaDomicilioRepository;

    @Transactional(readOnly = true)
    public List<DireccionResponse> listarMias(Long clienteId) {
        return direccionRepository.findByClienteUsuarioId(clienteId).stream().map(DireccionResponse::from).toList();
    }

    @Transactional
    public DireccionResponse crear(Long clienteId, DireccionRequest request) {
        var cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new BusinessRuleException("El usuario autenticado no es un cliente"));
        ZonaDomicilio zona = request.zonaDomicilioId() != null
                ? zonaDomicilioRepository.findById(request.zonaDomicilioId())
                    .orElseThrow(() -> new NotFoundException("No existe la zona " + request.zonaDomicilioId()))
                : null;
        Direccion direccion = Direccion.builder()
                .cliente(cliente)
                .zonaDomicilio(zona)
                .etiqueta(request.etiqueta())
                .direccionTexto(request.direccionTexto())
                .referencia(request.referencia())
                .predeterminada(request.predeterminada() != null ? request.predeterminada() : false)
                .build();
        return DireccionResponse.from(direccionRepository.save(direccion));
    }

    @Transactional
    public DireccionResponse editar(Long clienteId, Long id, DireccionRequest request) {
        Direccion direccion = buscarDeCliente(clienteId, id);
        if (request.etiqueta() != null) direccion.setEtiqueta(request.etiqueta());
        if (request.direccionTexto() != null) direccion.setDireccionTexto(request.direccionTexto());
        if (request.referencia() != null) direccion.setReferencia(request.referencia());
        if (request.predeterminada() != null) direccion.setPredeterminada(request.predeterminada());
        if (request.zonaDomicilioId() != null) {
            direccion.setZonaDomicilio(zonaDomicilioRepository.findById(request.zonaDomicilioId())
                    .orElseThrow(() -> new NotFoundException("No existe la zona " + request.zonaDomicilioId())));
        }
        return DireccionResponse.from(direccionRepository.save(direccion));
    }

    @Transactional
    public void eliminar(Long clienteId, Long id) {
        Direccion direccion = buscarDeCliente(clienteId, id);
        direccionRepository.delete(direccion);
    }

    private Direccion buscarDeCliente(Long clienteId, Long id) {
        Direccion direccion = direccionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("No existe la dirección " + id));
        if (!direccion.getCliente().getUsuarioId().equals(clienteId)) {
            throw new NotFoundException("No existe la dirección " + id);
        }
        return direccion;
    }
}
