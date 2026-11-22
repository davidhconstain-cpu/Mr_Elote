package com.vivanetpay.persistence;

import com.vivanetpay.domain.Actors;
import com.vivanetpay.domain.AuditEntry;
import com.vivanetpay.domain.EventStatus;
import com.vivanetpay.domain.PaymentEvent;
import com.vivanetpay.domain.Transaction;
import com.vivanetpay.domain.Validation;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

/**
 * Escribe en UNA transacción (la del llamador) evento + movimiento + validaciones + auditoría (IDD §7.2).
 * El llamador usa {@link Database#inTransaction}; si algo falla, no queda nada a medias.
 */
public final class IngestionWriter {
    private final PaymentEventRepository events;
    private final TransactionRepository transactions;
    private final ValidationRepository validations;
    private final AuditRepository audit;

    public IngestionWriter(PaymentEventRepository events, TransactionRepository transactions,
                           ValidationRepository validations, AuditRepository audit) {
        this.events = events;
        this.transactions = transactions;
        this.validations = validations;
        this.audit = audit;
    }

    /**
     * Registro a persistir.
     *
     * @param event        evento con su estado final (id null)
     * @param headersJson  cabeceras de origen en JSON (nullable)
     * @param extractedJson datos extraídos por el parser en JSON (nullable)
     * @param transaction  movimiento (eventId null) o null si no corresponde
     * @param validations  validaciones (eventId null)
     * @param statusTrail  recorrido de estados desde RECIBIDO hasta el final (para auditoría)
     */
    public record Record(PaymentEvent event, String headersJson, String extractedJson, Transaction transaction,
                         List<Validation> validations, List<EventStatus> statusTrail) {
        public Record {
            Objects.requireNonNull(event, "event");
            validations = List.copyOf(validations);
            statusTrail = List.copyOf(statusTrail);
            for (int i = 1; i < statusTrail.size(); i++) {
                statusTrail.get(i - 1).requireTransitionTo(statusTrail.get(i));
            }
            if (!statusTrail.isEmpty() && statusTrail.get(statusTrail.size() - 1) != event.status()) {
                throw new IllegalArgumentException("El recorrido de estados no termina en el estado del evento");
            }
        }
    }

    /** Ids generados. */
    public record Ids(long eventId, Long transactionId) {
    }

    public Ids write(Connection c, Record r) throws SQLException {
        PaymentEvent e = r.event();
        long eventId = events.insert(c, e, r.headersJson(), r.extractedJson());
        Long txId = null;
        if (r.transaction() != null) {
            Transaction t = r.transaction();
            txId = transactions.insert(c, new Transaction(null, eventId, t.bank(), t.amount(), t.currency(), t.sender(),
                    t.reference(), t.occurredAt(), t.createdAt()));
        }
        for (Validation v : r.validations()) {
            validations.insert(c, new Validation(null, eventId, v.rule(), v.result(), v.reason(), v.validatedAt(), v.actor()));
        }
        String id = Long.toString(eventId);
        audit.insert(c, new AuditEntry(null, AuditEntry.ENTITY_EVENT, id, AuditEntry.ACTION_CREATE, null,
                e.status().name(), Actors.SYSTEM, "Evento recibido de " + e.source() + " (" + e.connectorId() + ")",
                e.correlationId(), e.createdAt()));
        List<EventStatus> trail = r.statusTrail();
        for (int i = 1; i < trail.size(); i++) {
            String reason = i == trail.size() - 1 ? e.statusReason() : null;
            audit.insert(c, new AuditEntry(null, AuditEntry.ENTITY_EVENT, id, AuditEntry.ACTION_STATUS_CHANGE,
                    trail.get(i - 1).name(), trail.get(i).name(), Actors.SYSTEM, reason, e.correlationId(), e.createdAt()));
        }
        if (txId != null) {
            audit.insert(c, new AuditEntry(null, AuditEntry.ENTITY_TRANSACTION, Long.toString(txId), AuditEntry.ACTION_CREATE,
                    null, Long.toString(r.transaction().amount()), Actors.SYSTEM, null, e.correlationId(), e.createdAt()));
        }
        return new Ids(eventId, txId);
    }
}
