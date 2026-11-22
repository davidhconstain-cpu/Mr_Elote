package com.vivanetpay.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Evidencia de una validación automática o manual (tabla {@code validation}).
 *
 * @param id          id interno (null antes de persistir)
 * @param eventId     evento validado
 * @param rule        código de la regla (p. ej. "IMPORTE_POSITIVO", "MANUAL_VALIDAR")
 * @param result      resultado
 * @param reason      explicación en español
 * @param validatedAt instante UTC
 * @param actor       {@link Actors#SYSTEM} o el usuario que decidió
 */
public record Validation(Long id, Long eventId, String rule, ValidationResult result,
                         String reason, Instant validatedAt, String actor) {
    public Validation {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(validatedAt, "validatedAt");
        Objects.requireNonNull(actor, "actor");
    }
}
