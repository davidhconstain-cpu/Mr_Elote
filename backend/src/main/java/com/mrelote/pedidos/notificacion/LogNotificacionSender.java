package com.mrelote.pedidos.notificacion;

import com.mrelote.pedidos.entity.Notificacion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Entrega real (no simulada) de la notificación por el único canal que no
 * depende de credenciales de un tercero: el log de la aplicación. En
 * producción con un proveedor de SMS/email/WhatsApp real, esta clase se
 * reemplaza (o se compone junto a) una implementación que sí llame a ese
 * proveedor — el resto del sistema (NotificacionDeliveryJob, el estado
 * "enviada"/"fallida") no cambia.
 */
@Component
@Slf4j
public class LogNotificacionSender implements NotificacionSender {

    @Override
    public void enviar(Notificacion notificacion) {
        String destinatario = notificacion.getUsuario() != null
                ? notificacion.getUsuario().getNombre() + " <" + notificacion.getUsuario().getEmail() + ">"
                : "(sin destinatario registrado)";
        log.info("[notificacion:{}] canal={} pedido={} para {} -> {}",
                notificacion.getTipo(), notificacion.getCanal(),
                notificacion.getPedido() != null ? notificacion.getPedido().getId() : null,
                destinatario, notificacion.getContenido());
    }
}
