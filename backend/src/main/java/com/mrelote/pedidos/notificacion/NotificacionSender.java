package com.mrelote.pedidos.notificacion;

import com.mrelote.pedidos.entity.Notificacion;

/**
 * Canal real de entrega de una notificación. El proveedor real (SMS,
 * email, WhatsApp) queda pendiente de la decisión de sección 28 del
 * documento de requisitos, que requiere credenciales de un proveedor
 * externo que este entorno no tiene; {@link LogNotificacionSender} es una
 * implementación real (no un stub) sobre un canal que sí existe siempre:
 * el log de la aplicación.
 */
public interface NotificacionSender {
    void enviar(Notificacion notificacion);
}
