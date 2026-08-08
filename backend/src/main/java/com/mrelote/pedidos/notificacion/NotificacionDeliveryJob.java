package com.mrelote.pedidos.notificacion;

import com.mrelote.pedidos.entity.Notificacion;
import com.mrelote.pedidos.repository.NotificacionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Toma las notificaciones en estado "pendiente" y las entrega por su
 * {@link NotificacionSender}, dejándolas "enviada" o "fallida" según el
 * resultado — el paso que NotificacionService dejaba pendiente para una
 * fase posterior.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificacionDeliveryJob {

    private final NotificacionRepository notificacionRepository;
    private final NotificacionSender notificacionSender;

    @Scheduled(fixedDelayString = "${mrelote.notificaciones.intervalo-ms:5000}")
    @Transactional
    public void entregarPendientes() {
        List<Notificacion> pendientes = notificacionRepository.findByEstado("pendiente");
        for (Notificacion notificacion : pendientes) {
            try {
                notificacionSender.enviar(notificacion);
                notificacion.setEstado("enviada");
                notificacion.setEnviadoEn(OffsetDateTime.now());
            } catch (Exception e) {
                log.warn("No se pudo entregar la notificación {}: {}", notificacion.getId(), e.getMessage());
                notificacion.setEstado("fallida");
            }
            notificacionRepository.save(notificacion);
        }
    }
}
