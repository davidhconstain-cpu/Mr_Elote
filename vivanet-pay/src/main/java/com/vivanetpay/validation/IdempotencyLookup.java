package com.vivanetpay.validation;

import com.vivanetpay.domain.EventSource;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Consultas que necesita el {@link IdempotencyEngine}. Implementado por la persistencia
 * ({@code PaymentEventRepository}). Solo consideran eventos "originales" (no marcados DUPLICADO).
 */
public interface IdempotencyLookup {

    /** Evento original con esa clave de idempotencia. */
    Optional<Long> findByIdempotencyKey(String idempotencyKey);

    /** Evento original con la misma huella. */
    Optional<Long> findByFingerprint(String fingerprint);

    /** Eventos originales con el mismo importe cuya fecha efectiva cae en [from, to]. */
    List<SimilarEvent> findSameAmountBetween(long amount, Instant from, Instant to);

    /** Evento similar para detectar coincidencias parciales (CP-014). */
    record SimilarEvent(long eventId, EventSource source, String externalId, String sender, Instant occurredAt) {
    }
}
