package com.mrelote.pedidos.service;

import com.mrelote.pedidos.dto.request.MesaRequest;
import com.mrelote.pedidos.dto.response.MesaResponse;
import com.mrelote.pedidos.dto.response.QrMesaResponse;
import com.mrelote.pedidos.entity.Mesa;
import com.mrelote.pedidos.entity.QrMesa;
import com.mrelote.pedidos.exception.NotFoundException;
import com.mrelote.pedidos.repository.MesaRepository;
import com.mrelote.pedidos.repository.QrMesaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Sección 17: se puede regenerar un QR si el anterior se compromete, sin
 * perder la trazabilidad del anterior (se desactiva, no se borra).
 */
@Service
@RequiredArgsConstructor
public class MesaService {

    private final MesaRepository mesaRepository;
    private final QrMesaRepository qrMesaRepository;

    @Transactional(readOnly = true)
    public List<MesaResponse> listar() {
        return mesaRepository.findAll().stream().map(MesaResponse::from).toList();
    }

    @Transactional
    public MesaResponse crear(MesaRequest request) {
        Mesa mesa = Mesa.builder()
                .numero(request.numero())
                .capacidad(request.capacidad())
                .activa(request.activa() != null ? request.activa() : true)
                .build();
        return MesaResponse.from(mesaRepository.save(mesa));
    }

    @Transactional
    public MesaResponse editar(Long id, MesaRequest request) {
        Mesa mesa = mesaRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("No existe la mesa " + id));
        if (request.numero() != null) mesa.setNumero(request.numero());
        if (request.capacidad() != null) mesa.setCapacidad(request.capacidad());
        if (request.activa() != null) mesa.setActiva(request.activa());
        return MesaResponse.from(mesaRepository.save(mesa));
    }

    @Transactional
    public QrMesaResponse regenerarQr(Long mesaId) {
        Mesa mesa = mesaRepository.findById(mesaId)
                .orElseThrow(() -> new NotFoundException("No existe la mesa " + mesaId));

        qrMesaRepository.findByMesaIdAndActivoTrue(mesaId).ifPresent(anterior -> {
            anterior.setActivo(false);
            qrMesaRepository.save(anterior);
        });

        QrMesa nuevo = QrMesa.builder()
                .mesa(mesa)
                .codigo(UUID.randomUUID().toString())
                .activo(true)
                .build();
        return QrMesaResponse.from(qrMesaRepository.save(nuevo));
    }

    @Transactional(readOnly = true)
    public MesaResponse resolverQr(String codigo) {
        QrMesa qr = qrMesaRepository.findByCodigoAndActivoTrue(codigo)
                .orElseThrow(() -> new NotFoundException("El código QR no existe o ya no está activo"));
        return MesaResponse.from(qr.getMesa());
    }
}
