package com.mrelote.pedidos.service;

import com.mrelote.pedidos.dto.request.ActualizarTarifaRequest;
import com.mrelote.pedidos.dto.request.ZonaDomicilioRequest;
import com.mrelote.pedidos.dto.response.ZonaDomicilioResponse;
import com.mrelote.pedidos.entity.TarifaDomicilio;
import com.mrelote.pedidos.entity.ZonaDomicilio;
import com.mrelote.pedidos.exception.NotFoundException;
import com.mrelote.pedidos.repository.TarifaDomicilioRepository;
import com.mrelote.pedidos.repository.ZonaDomicilioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * RF-030, RN-005, sección 18: la tarifa se versiona en el tiempo
 * (vigente_desde/vigente_hasta) en vez de sobrescribirse, para que un
 * pedido ya despachado no cambie de precio si la tarifa se actualiza
 * después.
 */
@Service
@RequiredArgsConstructor
public class ZonaDomicilioService {

    private final ZonaDomicilioRepository zonaDomicilioRepository;
    private final TarifaDomicilioRepository tarifaDomicilioRepository;
    private final AuditoriaService auditoriaService;

    @Transactional(readOnly = true)
    public List<ZonaDomicilioResponse> listar() {
        return zonaDomicilioRepository.findAll().stream().map(this::conTarifaVigente).toList();
    }

    @Transactional
    public ZonaDomicilioResponse crear(ZonaDomicilioRequest request) {
        ZonaDomicilio zona = ZonaDomicilio.builder()
                .nombre(request.nombre())
                .activa(request.activa() != null ? request.activa() : true)
                .build();
        zona = zonaDomicilioRepository.save(zona);
        tarifaDomicilioRepository.save(TarifaDomicilio.builder()
                .zonaDomicilio(zona)
                .tarifa(request.tarifa())
                .tiempoEstimadoMinutos(request.tiempoEstimadoMinutos())
                .build());
        return conTarifaVigente(zona);
    }

    @Transactional
    public ZonaDomicilioResponse actualizarTarifa(Long zonaId, ActualizarTarifaRequest request, Authentication auth) {
        ZonaDomicilio zona = zonaDomicilioRepository.findById(zonaId)
                .orElseThrow(() -> new NotFoundException("No existe la zona " + zonaId));

        BigDecimal anterior = tarifaDomicilioRepository.findByZonaDomicilioIdAndVigenteHastaIsNull(zonaId)
                .map(t -> {
                    t.setVigenteHasta(OffsetDateTime.now());
                    tarifaDomicilioRepository.save(t);
                    return t.getTarifa();
                })
                .orElse(null);

        tarifaDomicilioRepository.save(TarifaDomicilio.builder()
                .zonaDomicilio(zona)
                .tarifa(request.tarifa())
                .tiempoEstimadoMinutos(request.tiempoEstimadoMinutos())
                .build());

        auditoriaService.registrar(auth, "actualizar_tarifa", "zona_domicilio", zonaId, anterior, request.tarifa());
        return conTarifaVigente(zona);
    }

    private ZonaDomicilioResponse conTarifaVigente(ZonaDomicilio zona) {
        return tarifaDomicilioRepository.findByZonaDomicilioIdAndVigenteHastaIsNull(zona.getId())
                .map(t -> ZonaDomicilioResponse.from(zona, t.getTarifa(), t.getTiempoEstimadoMinutos()))
                .orElse(ZonaDomicilioResponse.from(zona, null, null));
    }
}
