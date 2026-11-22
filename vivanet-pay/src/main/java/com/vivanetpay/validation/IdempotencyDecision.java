package com.vivanetpay.validation;

import java.util.Objects;

/**
 * Decisión del motor de idempotencia.
 *
 * @param outcome        NUEVO, DUPLICADO o AMBIGUO
 * @param idempotencyKey clave calculada ("ext:FUENTE:id" o "fp:huella")
 * @param fingerprint    huella SHA-256
 * @param matchedEventId evento coincidente (DUPLICADO/AMBIGUO), nullable
 * @param reason         motivo en español (nullable para NUEVO)
 */
public record IdempotencyDecision(Outcome outcome, String idempotencyKey, String fingerprint,
                                  Long matchedEventId, String reason) {
    public enum Outcome { NUEVO, DUPLICADO, AMBIGUO }

    public IdempotencyDecision {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(fingerprint, "fingerprint");
    }

    public boolean isDuplicate() {
        return outcome == Outcome.DUPLICADO;
    }

    public boolean isAmbiguous() {
        return outcome == Outcome.AMBIGUO;
    }
}
