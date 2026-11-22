package com.vivanetpay.domain;

/** Estado de un anuncio de voz (tabla {@code voice_announcement}). */
public enum AnnouncementStatus {
    EN_COLA,
    REPRODUCIDO,
    /** No se reprodujo por política (voz silenciada, histórico, antiguo). El evento sigue visible. */
    OMITIDO,
    /** Falló el motor de voz; se muestra aviso visual (Flujo E paso 37). */
    FALLIDO
}
