package com.vivanetpay.voice;

import com.vivanetpay.domain.AnnouncementKind;
import com.vivanetpay.domain.EventStatus;

import java.time.Instant;
import java.util.Objects;

/**
 * Solicitud de anuncio.
 *
 * @param eventId       evento (nullable para alertas de conector)
 * @param transactionId movimiento (nullable)
 * @param kind          tipo; PAGO_CONFIRMADO exige {@code eventStatus.isConfirmed()} (RN-003)
 * @param eventStatus   estado del evento en el momento de encolar (nullable para alertas)
 * @param bank          entidad (nullable)
 * @param amount        pesos enteros (nullable)
 * @param sender        remitente (solo se dice si {@link VoiceSettings#includeSender()})
 * @param occurredAt    instante del pago (para descartar anuncios antiguos)
 * @param historical    true si viene de sincronización histórica: nunca se anuncia
 * @param sourceLabel   nombre de la fuente para alertas de conector (p. ej. "Gmail")
 * @param correlationId correlación
 */
public record AnnouncementRequest(Long eventId, Long transactionId, AnnouncementKind kind, EventStatus eventStatus,
                                  String bank, Long amount, String sender, Instant occurredAt, boolean historical,
                                  String sourceLabel, String correlationId) {
    public AnnouncementRequest {
        Objects.requireNonNull(kind, "kind");
        if (kind == AnnouncementKind.PAGO_CONFIRMADO && (eventStatus == null || !eventStatus.isConfirmed())) {
            throw new IllegalArgumentException("RN-003: 'pago confirmado' solo para eventos validados; estado=" + eventStatus);
        }
    }

    /** Alerta de conector desconectado. */
    public static AnnouncementRequest connectorAlert(String sourceLabel) {
        return new AnnouncementRequest(null, null, AnnouncementKind.ALERTA_CONECTOR, null, null, null, null,
                null, false, sourceLabel, null);
    }
}
