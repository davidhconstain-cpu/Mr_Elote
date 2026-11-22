package com.vivanetpay.voice;

import com.vivanetpay.domain.AnnouncementStatus;

import java.time.Instant;

/**
 * Resultado final de cada anuncio (REPRODUCIDO, OMITIDO o FALLIDO). Lo usa la capa de aplicación para
 * persistir {@code voice_announcement}, marcar ANUNCIADO y mostrar aviso visual si falla (Flujo E pasos 36-37).
 * Se invoca desde el hilo de la cola o del llamador; no bloquear.
 */
@FunctionalInterface
public interface AnnouncementOutcomeListener {
    void onOutcome(AnnouncementRequest request, String message, AnnouncementStatus status,
                   Instant queuedAt, Instant finishedAt, String error);
}
