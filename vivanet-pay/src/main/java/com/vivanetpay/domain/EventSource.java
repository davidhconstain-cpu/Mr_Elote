package com.vivanetpay.domain;

/**
 * Fuente de origen de un evento (Anexo B, campo {@code source}).
 * Añadir una fuente nueva requiere cambio aprobado (IDD §15.2).
 */
public enum EventSource {
    /** Correo leído por el conector Gmail (OAuth solo lectura). */
    GMAIL,
    /** Notificación push de Nequi capturada en Android por MacroDroid y enviada por webhook HTTPS. */
    ANDROID_NEQUI,
    /** Notificación de otra app Android (reservado; no forma parte del MVP). */
    ANDROID_OTHER,
    /** Registro manual o de prueba introducido por el usuario. */
    MANUAL
}
