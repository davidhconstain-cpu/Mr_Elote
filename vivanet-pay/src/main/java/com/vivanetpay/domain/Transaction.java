package com.vivanetpay.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Movimiento normalizado que se presenta en historial y tablero (tabla {@code payment_transaction};
 * en el IDD §7.1 la entidad se llama {@code transaction}).
 * <p>
 * El importe es un {@code long} en pesos COP enteros (RN-007); la moneda es siempre COP (RN-006).
 * El estado de confirmación NO vive aquí, sino en el {@link PaymentEvent} asociado.
 *
 * @param id         id interno (null antes de persistir)
 * @param eventId    evento de origen (1:1)
 * @param bank       entidad normalizada (p. ej. "Nequi"); nullable si no se identificó
 * @param amount     pesos enteros, &gt; 0
 * @param currency   siempre "COP"
 * @param sender     remitente extraído (nombre de quien paga), nullable
 * @param reference  referencia extraída, nullable
 * @param occurredAt instante del pago (UTC)
 * @param createdAt  creación del registro (UTC)
 */
public record Transaction(Long id, Long eventId, String bank, long amount, String currency,
                          String sender, String reference, Instant occurredAt, Instant createdAt) {
    public Transaction {
        if (!CopAmount.isValid(amount)) {
            throw new IllegalArgumentException("Importe inválido (debe ser > 0 pesos): " + amount);
        }
        currency = currency == null ? CopAmount.CURRENCY : currency.trim().toUpperCase();
        if (!CopAmount.CURRENCY.equals(currency)) {
            throw new IllegalArgumentException("Moneda no admitida (RN-006): " + currency);
        }
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    /** Importe en formato colombiano, p. ej. "$125.000". */
    public String formattedAmount() {
        return CopAmount.format(amount);
    }
}
