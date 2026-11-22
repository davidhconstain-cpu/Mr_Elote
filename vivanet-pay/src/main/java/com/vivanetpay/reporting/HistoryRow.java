package com.vivanetpay.reporting;

import com.vivanetpay.domain.AppTime;
import com.vivanetpay.domain.CopAmount;
import com.vivanetpay.domain.EventSource;
import com.vivanetpay.domain.EventStatus;

import java.time.Instant;

/**
 * Fila del historial (RF-014): un evento con su movimiento (si lo hay).
 *
 * @param eventId                    id del evento
 * @param transactionId              id del movimiento (nullable si no se extrajo importe o fue duplicado/rechazado)
 * @param source                     fuente
 * @param connectorId                conector
 * @param bank                       entidad (nullable)
 * @param amount                     pesos (nullable)
 * @param sender                     remitente (nullable)
 * @param reference                  referencia (nullable)
 * @param status                     estado
 * @param statusReason               motivo del estado
 * @param occurredAt                 fecha efectiva del pago (UTC)
 * @param receivedAt                 recepción (UTC)
 * @param duplicateOfEventId         original si es duplicado
 * @param possibleDuplicateOfEventId coincidencia ambigua
 */
public record HistoryRow(long eventId, Long transactionId, EventSource source, String connectorId, String bank,
                         Long amount, String sender, String reference, EventStatus status, String statusReason,
                         Instant occurredAt, Instant receivedAt, Long duplicateOfEventId,
                         Long possibleDuplicateOfEventId) {

    public String formattedAmount() {
        return amount == null ? "" : CopAmount.format(amount);
    }

    /** Fecha/hora local de Colombia. */
    public String formattedOccurredAt() {
        return AppTime.format(occurredAt);
    }
}
