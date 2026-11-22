package com.vivanetpay.validation;

import com.vivanetpay.domain.EventSource;

import java.time.Instant;
import java.util.Objects;

/**
 * Datos normalizados de un evento entrante sobre los que se calcula la idempotencia (RN-004/RN-005).
 *
 * @param source      fuente
 * @param connectorId conector/cuenta
 * @param externalId  identificador externo (nullable)
 * @param occurredAt  fecha/hora efectiva del pago
 * @param amount      pesos enteros (nullable si no se extrajo)
 * @param bank        entidad (nullable)
 * @param sender      remitente (nullable)
 * @param reference   referencia (nullable)
 * @param rawText     texto original (se normaliza para la huella)
 */
public record EventCandidate(EventSource source, String connectorId, String externalId, Instant occurredAt,
                             Long amount, String bank, String sender, String reference, String rawText) {
    public EventCandidate {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(connectorId, "connectorId");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
