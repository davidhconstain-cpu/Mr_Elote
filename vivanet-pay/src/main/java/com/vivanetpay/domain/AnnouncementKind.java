package com.vivanetpay.domain;

/** Tipo de anuncio de voz (IDD §9.3). */
public enum AnnouncementKind {
    /** No anunciar. */
    NONE,
    /** "Nueva notificación de [entidad] por [monto] pesos. Verifica el pago." */
    POSIBLE_PAGO,
    /** "Pago recibido por [monto] pesos desde [entidad]." Solo para eventos confirmados (RN-003). */
    PAGO_CONFIRMADO,
    /** "Atención: la conexión de [fuente] está desconectada." */
    ALERTA_CONECTOR
}
