package com.mrelote.pedidos.service;

import com.mrelote.pedidos.entity.Notificacion;
import com.mrelote.pedidos.entity.Pedido;
import com.mrelote.pedidos.repository.NotificacionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-023, RF-046, sección 14: se dispara automáticamente en cada
 * transición relevante del pedido. El envío real por SMS/email/WhatsApp
 * queda pendiente de la decisión de proveedor (sección 28); aquí se deja
 * el registro en estado "pendiente" — un job o listener asíncrono
 * posterior sería el responsable de entregarla y marcarla "enviada".
 */
@Service
@RequiredArgsConstructor
public class NotificacionService {

    private final NotificacionRepository notificacionRepository;

    @Transactional
    public void notificar(Pedido pedido, String tipo, String contenido) {
        Notificacion notificacion = Notificacion.builder()
                .pedido(pedido)
                .usuario(pedido.getCliente() != null ? pedido.getCliente().getUsuario() : null)
                .canal("web")
                .tipo(tipo)
                .contenido(contenido)
                .estado("pendiente")
                .build();
        notificacionRepository.save(notificacion);
    }
}
