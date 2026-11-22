package com.vivanetpay.application;

import com.vivanetpay.domain.AnnouncementKind;
import com.vivanetpay.domain.EventStatus;

/**
 * Resultado de ingerir un {@link com.vivanetpay.domain.RawEvent}.
 *
 * @param eventId        id del evento persistido (null solo si ni siquiera pudo guardarse como ERROR)
 * @param status         estado final
 * @param transactionId  movimiento creado (nullable)
 * @param matchedEventId evento coincidente si es DUPLICADO o ambiguo (nullable)
 * @param reason         motivo del estado
 * @param announcement   anuncio solicitado
 * @param correlationId  correlación
 */
public record IngestionResult(Long eventId, EventStatus status, Long transactionId, Long matchedEventId,
                              String reason, AnnouncementKind announcement, String correlationId) {

    /** @return true si el evento quedó persistido (en cualquier estado): el conector puede avanzar su cursor. */
    public boolean isPersisted() {
        return eventId != null;
    }

    public boolean isDuplicate() {
        return status == EventStatus.DUPLICADO;
    }
}
