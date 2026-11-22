package com.vivanetpay.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Evento de pago persistido (tabla {@code payment_event}, IDD §7.1). Representa cada aviso recibido,
 * incluso si queda pendiente, duplicado o rechazado. El contenido original (raw*) nunca se sobrescribe
 * (Flujo D, paso 30); los cambios posteriores solo afectan {@code status}/{@code statusReason} y
 * quedan en {@code audit_log}.
 *
 * @param id                       id interno (null antes de persistir)
 * @param source                   fuente
 * @param connectorId              conector de captura
 * @param externalId               identificador externo (nullable)
 * @param sourceTimestamp          timestamp de origen UTC (nullable)
 * @param receivedAt               recepción UTC
 * @param rawSubject               asunto/título original
 * @param rawSender                remitente técnico original
 * @param rawText                  texto original permitido
 * @param status                   estado actual (RN-002)
 * @param statusReason             motivo del estado actual (p. ej. motivo de revisión)
 * @param fingerprint              huella SHA-256 (RN-005)
 * @param idempotencyKey           clave de idempotencia (null para eventos DUPLICADO)
 * @param duplicateOfEventId       evento original si es DUPLICADO
 * @param possibleDuplicateOfEventId evento con coincidencia parcial ambigua (CP-014)
 * @param parserId                 parser que produjo el resultado
 * @param parseConfidence          confianza 0..1 del parser
 * @param correlationId            correlación de logs
 * @param historical               capturado en sincronización histórica (no se anuncia)
 * @param announcedAt              instante del último anuncio de voz (nullable)
 * @param createdAt                creación del registro
 * @param updatedAt                última actualización de estado
 */
public record PaymentEvent(Long id,
                           EventSource source,
                           String connectorId,
                           String externalId,
                           Instant sourceTimestamp,
                           Instant receivedAt,
                           String rawSubject,
                           String rawSender,
                           String rawText,
                           EventStatus status,
                           String statusReason,
                           String fingerprint,
                           String idempotencyKey,
                           Long duplicateOfEventId,
                           Long possibleDuplicateOfEventId,
                           String parserId,
                           Double parseConfidence,
                           String correlationId,
                           boolean historical,
                           Instant announcedAt,
                           Instant createdAt,
                           Instant updatedAt) {
    public PaymentEvent {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(receivedAt, "receivedAt");
        Objects.requireNonNull(status, "status");
    }

    /** Instante que define "cuándo ocurrió" el pago: timestamp de origen o, si falta, la recepción. */
    public Instant effectiveTimestamp() {
        return sourceTimestamp != null ? sourceTimestamp : receivedAt;
    }
}
