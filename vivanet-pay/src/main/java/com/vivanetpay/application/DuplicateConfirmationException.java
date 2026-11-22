package com.vivanetpay.application;

/** Confirmar el evento contaría dos veces el mismo pago (R-04, CP-014): el par ya fue confirmado. */
public final class DuplicateConfirmationException extends IllegalStateException {
    private final long eventId;
    private final long confirmedEventId;

    public DuplicateConfirmationException(long eventId, long confirmedEventId) {
        super("Este pago ya fue confirmado por el evento #" + confirmedEventId + "; descártalo como duplicado");
        this.eventId = eventId;
        this.confirmedEventId = confirmedEventId;
    }

    public long eventId() {
        return eventId;
    }

    public long confirmedEventId() {
        return confirmedEventId;
    }
}
