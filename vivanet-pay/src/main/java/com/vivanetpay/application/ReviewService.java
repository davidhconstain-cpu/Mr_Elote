package com.vivanetpay.application;

import com.vivanetpay.domain.AnnouncementKind;
import com.vivanetpay.domain.AuditEntry;
import com.vivanetpay.domain.EventStatus;
import com.vivanetpay.domain.PaymentEvent;
import com.vivanetpay.domain.Transaction;
import com.vivanetpay.domain.Validation;
import com.vivanetpay.domain.ValidationResult;
import com.vivanetpay.persistence.AuditRepository;
import com.vivanetpay.persistence.Database;
import com.vivanetpay.persistence.PaymentEventRepository;
import com.vivanetpay.persistence.ReportingQueries;
import com.vivanetpay.persistence.TransactionRepository;
import com.vivanetpay.persistence.ValidationRepository;
import com.vivanetpay.validation.ValidationPolicyConfig;
import com.vivanetpay.voice.AnnouncementRequest;
import com.vivanetpay.voice.VoiceService;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * Revisión manual (Flujo D, RF-023, RF-024). Cada decisión registra actor, acción, fecha y motivo en
 * {@code validation} y {@code audit_log}, sin sobrescribir el contenido original del evento.
 * <p>
 * Con la política provisional de DP-01, la validación manual es la ÚNICA vía para que un pago quede
 * VALIDADO_POR_FUENTE ("pago confirmado"). Quién puede corregir y qué evidencia se exige es DP-12 (pendiente):
 * por ahora solo se exige un actor no vacío y motivo para descartar.
 * Los métodos acceden a la base: no llamarlos desde el hilo de UI.
 */
public final class ReviewService {
    public static final String RULE_MANUAL_VALIDATE = "MANUAL_VALIDAR";
    public static final String RULE_MANUAL_DISCARD = "MANUAL_DESCARTAR";
    public static final String RULE_MANUAL_KEEP = "MANUAL_MANTENER";

    private final Database db;
    private final PaymentEventRepository events;
    private final TransactionRepository transactions;
    private final ValidationRepository validations;
    private final AuditRepository audit;
    private final ReportingQueries queries;
    private final AppEventBus bus;
    private final VoiceService voice;
    private final ValidationPolicyConfig policyConfig;
    private final Clock clock;

    public ReviewService(Database db, PaymentEventRepository events, TransactionRepository transactions,
                         ValidationRepository validations, AuditRepository audit, ReportingQueries queries,
                         AppEventBus bus, VoiceService voice, ValidationPolicyConfig policyConfig, Clock clock) {
        this.db = db;
        this.events = events;
        this.transactions = transactions;
        this.validations = validations;
        this.audit = audit;
        this.queries = queries;
        this.bus = bus;
        this.voice = voice;
        this.policyConfig = policyConfig;
        this.clock = clock;
    }

    /** Eventos en bandeja de revisión (PARSEADO y PENDIENTE_REVISION), más recientes primero. */
    public List<ReviewItem> pending(int limit) {
        List<ReviewItem> out = new ArrayList<>();
        for (PaymentEvent e : events.findByStatuses(EnumSet.of(EventStatus.PARSEADO, EventStatus.PENDIENTE_REVISION), limit)) {
            queries.row(e.id()).ifPresent(row -> out.add(new ReviewItem(row, e.rawSubject(), e.rawText(),
                    validations.findByEvent(e.id()))));
        }
        return out;
    }

    /** Detalle de un evento para revisión. */
    public Optional<ReviewItem> detail(long eventId) {
        return events.findById(eventId).flatMap(e -> queries.row(eventId)
                .map(row -> new ReviewItem(row, e.rawSubject(), e.rawText(), validations.findByEvent(eventId))));
    }

    /**
     * El usuario confirma el pago tras contrastarlo con el canal oficial (Flujo D paso 28-31).
     *
     * @throws IllegalStateException si el evento no tiene importe o la transición no está permitida
     */
    public EventStatus validate(long eventId, String actor, String reason) {
        requireActor(actor);
        Instant now = clock.instant();
        String why = reason == null || reason.isBlank() ? "Validado manualmente contra el canal oficial" : reason.trim();
        EventStatus previous = db.inTransaction(c -> {
            Optional<Transaction> tx = transactions.findByEventId(c, eventId);
            if (tx.isEmpty()) {
                throw new IllegalStateException("No se puede confirmar un evento sin importe extraído (corrección pendiente DP-12)");
            }
            EventStatus prev = events.updateStatus(c, eventId, EventStatus.VALIDADO_POR_FUENTE, why, now);
            validations.insert(c, new Validation(null, eventId, RULE_MANUAL_VALIDATE, ValidationResult.APROBADO, why, now, actor));
            audit.insert(c, new AuditEntry(null, AuditEntry.ENTITY_EVENT, Long.toString(eventId),
                    AuditEntry.ACTION_STATUS_CHANGE, prev.name(), EventStatus.VALIDADO_POR_FUENTE.name(), actor, why, null, now));
            return prev;
        });
        bus.publish(new AppEvent.EventStatusChanged(eventId, previous, EventStatus.VALIDADO_POR_FUENTE, actor));
        if (voice != null && policyConfig.announceOnManualValidation()) {
            transactions.findByEventId(eventId).ifPresent(t -> voice.enqueue(new AnnouncementRequest(eventId, t.id(),
                    AnnouncementKind.PAGO_CONFIRMADO, EventStatus.VALIDADO_POR_FUENTE, t.bank(), t.amount(), t.sender(),
                    null, false, null, null)));
        }
        return EventStatus.VALIDADO_POR_FUENTE;
    }

    /** El usuario descarta el evento (queda RECHAZADO, nunca se borra). Motivo obligatorio. */
    public EventStatus discard(long eventId, String actor, String reason) {
        requireActor(actor);
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Debe indicar el motivo del descarte");
        }
        Instant now = clock.instant();
        EventStatus previous = db.inTransaction(c -> {
            EventStatus prev = events.updateStatus(c, eventId, EventStatus.RECHAZADO, reason.trim(), now);
            validations.insert(c, new Validation(null, eventId, RULE_MANUAL_DISCARD, ValidationResult.FALLIDO,
                    reason.trim(), now, actor));
            audit.insert(c, new AuditEntry(null, AuditEntry.ENTITY_EVENT, Long.toString(eventId),
                    AuditEntry.ACTION_STATUS_CHANGE, prev.name(), EventStatus.RECHAZADO.name(), actor, reason.trim(), null, now));
            return prev;
        });
        bus.publish(new AppEvent.EventStatusChanged(eventId, previous, EventStatus.RECHAZADO, actor));
        return EventStatus.RECHAZADO;
    }

    /** El usuario decide mantener pendiente: no cambia el estado, solo deja constancia. */
    public void keepPending(long eventId, String actor, String note) {
        requireActor(actor);
        Instant now = clock.instant();
        String why = note == null || note.isBlank() ? "Se mantiene pendiente" : note.trim();
        db.inTransaction(c -> {
            PaymentEvent e = events.findById(c, eventId)
                    .orElseThrow(() -> new IllegalStateException("Evento no existe: " + eventId));
            if (!e.status().isReviewable()) {
                throw new IllegalStateException("El evento no está en revisión: " + e.status());
            }
            validations.insert(c, new Validation(null, eventId, RULE_MANUAL_KEEP, ValidationResult.INFORMATIVO, why, now, actor));
            audit.insert(c, new AuditEntry(null, AuditEntry.ENTITY_EVENT, Long.toString(eventId),
                    AuditEntry.ACTION_REVIEW_KEEP, e.status().name(), e.status().name(), actor, why, null, now));
            return null;
        });
    }

    private static void requireActor(String actor) {
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("Debe indicarse el usuario que realiza la revisión");
        }
    }
}
