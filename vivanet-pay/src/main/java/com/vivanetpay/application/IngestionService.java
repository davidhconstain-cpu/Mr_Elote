package com.vivanetpay.application;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.vivanetpay.domain.Actors;
import com.vivanetpay.domain.AnnouncementKind;
import com.vivanetpay.domain.CopAmount;
import com.vivanetpay.domain.EventStatus;
import com.vivanetpay.domain.PaymentEvent;
import com.vivanetpay.domain.RawEvent;
import com.vivanetpay.domain.Transaction;
import com.vivanetpay.domain.Validation;
import com.vivanetpay.domain.ValidationResult;
import com.vivanetpay.observability.Correlation;
import com.vivanetpay.parsing.ParseResult;
import com.vivanetpay.parsing.ParserRegistry;
import com.vivanetpay.persistence.Database;
import com.vivanetpay.persistence.IngestionWriter;
import com.vivanetpay.persistence.PaymentEventRepository;
import com.vivanetpay.persistence.PersistenceException;
import com.vivanetpay.validation.EventCandidate;
import com.vivanetpay.validation.EventValidator;
import com.vivanetpay.validation.Fingerprint;
import com.vivanetpay.validation.IdempotencyDecision;
import com.vivanetpay.validation.IdempotencyEngine;
import com.vivanetpay.validation.PolicyDecision;
import com.vivanetpay.validation.PolicyInput;
import com.vivanetpay.validation.RuleCheck;
import com.vivanetpay.validation.ValidationPolicy;
import com.vivanetpay.voice.AnnouncementRequest;
import com.vivanetpay.voice.VoiceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Pipeline de ingesta (Flujos B y C): RECIBIDO → análisis → reglas (importe &gt; 0, COP) → idempotencia →
 * política de validación → persistencia atómica → {@link AppEvent} → anuncio según política.
 * <p>
 * Se ejecuta en un único hilo de fondo ("vivanet-ingesta"), nunca en el hilo de UI (RNF-008). La decisión de
 * idempotencia y la inserción ocurren en la MISMA transacción IMMEDIATE; si aun así una carrera entre procesos
 * viola un UNIQUE, se reevalúa (y el evento queda DUPLICADO). Un fallo inesperado se persiste como ERROR
 * para no perder el evento (RF-006).
 */
public final class IngestionService implements EventSink, AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);
    private static final int MAX_ATTEMPTS = 3;
    public static final String RULE_IDEMPOTENCY = "IDEMPOTENCIA";
    public static final String RULE_POLICY_PREFIX = "POLITICA:";

    private final Database db;
    private final PaymentEventRepository events;
    private final IngestionWriter writer;
    private final ParserRegistry parsers;
    private final EventValidator validator;
    private final IdempotencyEngine idempotency;
    private final ValidationPolicy policy;
    private final AppEventBus bus;
    private final VoiceService voice;
    private final ObjectMapper json;
    private final Clock clock;
    private final ExecutorService executor;

    public IngestionService(Database db, PaymentEventRepository events, IngestionWriter writer, ParserRegistry parsers,
                            EventValidator validator, IdempotencyEngine idempotency, ValidationPolicy policy,
                            AppEventBus bus, VoiceService voice, ObjectMapper json, Clock clock) {
        this.db = Objects.requireNonNull(db);
        this.events = Objects.requireNonNull(events);
        this.writer = Objects.requireNonNull(writer);
        this.parsers = Objects.requireNonNull(parsers);
        this.validator = Objects.requireNonNull(validator);
        this.idempotency = Objects.requireNonNull(idempotency);
        this.policy = Objects.requireNonNull(policy);
        this.bus = Objects.requireNonNull(bus);
        this.voice = voice;
        this.json = Objects.requireNonNull(json);
        this.clock = Objects.requireNonNull(clock);
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "vivanet-ingesta");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public IngestionResult ingest(RawEvent event) {
        try {
            return ingestAsync(event).join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException(cause);
        }
    }

    @Override
    public CompletableFuture<IngestionResult> ingestAsync(RawEvent event) {
        Objects.requireNonNull(event, "event");
        return CompletableFuture.supplyAsync(() -> process(event), executor);
    }

    private IngestionResult process(RawEvent raw) {
        try (var c = Correlation.open(raw.correlationId())) {
            log.info("Evento recibido: fuente={} conector={} externo={}", raw.source(), raw.connectorId(),
                    raw.externalEventId() != null ? "sí" : "no");
            try {
                return doProcess(raw);
            } catch (RuntimeException e) {
                log.error("Fallo procesando evento; se registra como ERROR", e);
                return persistError(raw, e);
            }
        }
    }

    /** Resultado interno de la transacción. */
    private record Outcome(IngestionWriter.Ids ids, EventStatus status, String reason, AnnouncementKind announcement,
                           Long matchedEventId, Transaction transaction) {
    }

    private IngestionResult doProcess(RawEvent raw) {
        Instant now = clock.instant();
        ParseResult parse = parsers.parse(raw);
        List<RuleCheck> checks = validator.check(raw, parse, now);
        Instant occurredAt = parse.occurredAt() != null ? parse.occurredAt()
                : raw.sourceTimestamp() != null ? raw.sourceTimestamp() : raw.receivedAt();
        Long amount = parse.recognized() ? parse.amount() : null;
        EventCandidate candidate = new EventCandidate(raw.source(), raw.connectorId(), raw.externalEventId(), occurredAt,
                amount, parse.bank(), parse.sender(), parse.reference(), raw.rawText());
        String headersJson = toJson(raw.headers());
        String extractedJson = toJson(extracted(parse));

        Outcome outcome = null;
        for (int attempt = 1; outcome == null; attempt++) {
            try {
                outcome = db.inTransaction(c -> decideAndWrite(c, raw, parse, checks, candidate, occurredAt, headersJson,
                        extractedJson, now));
            } catch (PersistenceException e) {
                if (!e.isUniqueViolation() || attempt >= MAX_ATTEMPTS) {
                    throw e;
                }
                log.info("Carrera de idempotencia detectada (UNIQUE); reevaluando (intento {})", attempt + 1);
            }
        }

        long eventId = outcome.ids().eventId();
        log.info("Evento #{} persistido con estado {}", eventId, outcome.status());
        if (outcome.ids().transactionId() != null) {
            bus.publish(new AppEvent.TransactionRecorded(eventId, outcome.ids().transactionId(), outcome.status(),
                    raw.source(), outcome.transaction().bank(), outcome.transaction().amount(), occurredAt));
        }
        if (outcome.status().isReviewable()) {
            bus.publish(new AppEvent.EventNeedsReview(eventId, outcome.status(), outcome.reason()));
        }
        if (voice != null && outcome.announcement() != AnnouncementKind.NONE) {
            Transaction t = outcome.transaction();
            voice.enqueue(new AnnouncementRequest(eventId, outcome.ids().transactionId(), outcome.announcement(),
                    outcome.status(), t != null ? t.bank() : parse.bank(), t != null ? t.amount() : null,
                    t != null ? t.sender() : null, occurredAt, raw.historical(), null, raw.correlationId()));
        }
        return new IngestionResult(eventId, outcome.status(), outcome.ids().transactionId(), outcome.matchedEventId(),
                outcome.reason(), outcome.announcement(), raw.correlationId());
    }

    private Outcome decideAndWrite(java.sql.Connection c, RawEvent raw, ParseResult parse, List<RuleCheck> checks,
                                   EventCandidate candidate, Instant occurredAt, String headersJson,
                                   String extractedJson, Instant now) throws java.sql.SQLException {
        IdempotencyDecision idem = idempotency.evaluate(candidate, events.lookup(c));
        List<EventStatus> trail = new ArrayList<>();
        trail.add(EventStatus.RECIBIDO);
        if (parse.recognized()) {
            trail.add(EventStatus.PARSEADO);
        }
        EventStatus finalStatus;
        String reason;
        AnnouncementKind announcement;
        List<Validation> validations = new ArrayList<>();
        for (RuleCheck rc : checks) {
            validations.add(new Validation(null, null, rc.rule(), rc.result(), rc.reason(), now, Actors.SYSTEM));
        }
        if (idem.isDuplicate()) {
            finalStatus = EventStatus.DUPLICADO;
            reason = idem.reason();
            announcement = AnnouncementKind.NONE;
            validations.add(new Validation(null, null, RULE_IDEMPOTENCY, ValidationResult.FALLIDO, reason, now, Actors.SYSTEM));
        } else {
            validations.add(new Validation(null, null, RULE_IDEMPOTENCY,
                    idem.isAmbiguous() ? ValidationResult.ADVERTENCIA : ValidationResult.APROBADO,
                    idem.isAmbiguous() ? idem.reason() : "Evento nuevo", now, Actors.SYSTEM));
            PolicyDecision decision = policy.decide(new PolicyInput(raw, parse, checks, idem));
            finalStatus = decision.status();
            reason = decision.reason();
            announcement = decision.announcement();
            ValidationResult pr = switch (finalStatus) {
                case VALIDADO_POR_FUENTE -> ValidationResult.APROBADO;
                case RECHAZADO -> ValidationResult.FALLIDO;
                default -> ValidationResult.ADVERTENCIA;
            };
            validations.add(new Validation(null, null, RULE_POLICY_PREFIX + policy.id(), pr, reason, now, Actors.SYSTEM));
        }
        if (trail.getLast() != finalStatus) {
            trail.add(finalStatus);
        }

        Transaction tx = null;
        boolean countable = finalStatus != EventStatus.DUPLICADO && finalStatus != EventStatus.RECHAZADO
                && finalStatus != EventStatus.ERROR;
        if (countable && candidate.amount() != null && CopAmount.isValid(candidate.amount())
                && CopAmount.isSupportedCurrency(parse.currency())) {
            tx = new Transaction(null, null, parse.bank(), candidate.amount(), CopAmount.CURRENCY, parse.sender(),
                    parse.reference(), occurredAt, now);
        }
        PaymentEvent event = new PaymentEvent(null, raw.source(), raw.connectorId(), raw.externalEventId(),
                raw.sourceTimestamp(), raw.receivedAt(), raw.subject(), raw.sender(), raw.rawText(), finalStatus, reason,
                idem.fingerprint(), idem.isDuplicate() ? null : idem.idempotencyKey(),
                idem.isDuplicate() ? idem.matchedEventId() : null, idem.isAmbiguous() ? idem.matchedEventId() : null,
                parse.parserId(), parse.confidence(), raw.correlationId(), raw.historical(), null, now, now);
        IngestionWriter.Ids ids = writer.write(c, new IngestionWriter.Record(event, headersJson, extractedJson, tx,
                validations, trail));
        return new Outcome(ids, finalStatus, reason, announcement, idem.matchedEventId(), tx);
    }

    private IngestionResult persistError(RawEvent raw, RuntimeException cause) {
        Instant now = clock.instant();
        String token = "err:" + UUID.randomUUID();
        String reason = "Error técnico al procesar: " + cause.getClass().getSimpleName();
        try {
            PaymentEvent event = new PaymentEvent(null, raw.source(), raw.connectorId(), raw.externalEventId(),
                    raw.sourceTimestamp(), raw.receivedAt(), raw.subject(), raw.sender(), raw.rawText(), EventStatus.ERROR,
                    reason, Fingerprint.sha256(token), token, null, null, null, null, raw.correlationId(),
                    raw.historical(), null, now, now);
            IngestionWriter.Ids ids = db.inTransaction(c -> writer.write(c, new IngestionWriter.Record(event,
                    toJsonSafe(raw.headers()), null, null, List.of(), List.of(EventStatus.RECIBIDO, EventStatus.ERROR))));
            return new IngestionResult(ids.eventId(), EventStatus.ERROR, null, null, reason, AnnouncementKind.NONE,
                    raw.correlationId());
        } catch (RuntimeException e) {
            log.error("No se pudo registrar el evento en estado ERROR", e);
            return new IngestionResult(null, EventStatus.ERROR, null, null, reason, AnnouncementKind.NONE,
                    raw.correlationId());
        }
    }

    private static Map<String, Object> extracted(ParseResult p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("parserId", p.parserId());
        m.put("recognized", p.recognized());
        m.put("amount", p.amount());
        m.put("currency", p.currency());
        m.put("bank", p.bank());
        m.put("sender", p.sender());
        m.put("reference", p.reference());
        m.put("occurredAt", p.occurredAt() != null ? p.occurredAt().toString() : null);
        m.put("confidence", p.confidence());
        m.put("reason", p.reason());
        return m;
    }

    private String toJson(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (JacksonException e) {
            throw new IllegalStateException("No se pudo serializar a JSON", e);
        }
    }

    private String toJsonSafe(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (JacksonException e) {
            return null;
        }
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
