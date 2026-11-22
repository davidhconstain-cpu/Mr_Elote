package com.vivanetpay.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Registro de un anuncio de voz (tabla {@code voice_announcement}).
 *
 * @param id            id interno
 * @param eventId       evento relacionado (nullable para alertas de conector)
 * @param transactionId movimiento relacionado (nullable)
 * @param kind          tipo de anuncio
 * @param message       texto anunciado (sin secretos)
 * @param status        resultado
 * @param queuedAt      instante de encolado
 * @param spokenAt      instante de reproducción (nullable)
 * @param error         descripción del fallo (nullable)
 */
public record VoiceAnnouncement(Long id, Long eventId, Long transactionId, AnnouncementKind kind,
                                String message, AnnouncementStatus status, Instant queuedAt,
                                Instant spokenAt, String error) {
    public VoiceAnnouncement {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(queuedAt, "queuedAt");
    }
}
