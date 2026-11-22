package com.vivanetpay.validation;

import com.vivanetpay.domain.EventSource;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyEngineTest {
    private static final Instant T = Instant.parse("2026-11-22T15:00:00Z");

    /** Lookup en memoria. */
    static final class MemLookup implements IdempotencyLookup {
        final Map<String, Long> keys = new HashMap<>();
        final Map<String, Long> fps = new HashMap<>();
        final List<SimilarEvent> similar = new ArrayList<>();
        final Map<Long, Long> amounts = new HashMap<>();

        void add(long id, EventCandidate c) {
            String fp = Fingerprint.of(c);
            keys.put(IdempotencyEngine.idempotencyKey(c, fp), id);
            fps.put(fp, id);
            if (c.amount() != null) {
                similar.add(new SimilarEvent(id, c.source(), c.externalId(), c.sender(), c.occurredAt()));
                amounts.put(id, c.amount());
            }
        }

        @Override
        public Optional<Long> findByIdempotencyKey(String k) {
            return Optional.ofNullable(keys.get(k));
        }

        @Override
        public Optional<Long> findByFingerprint(String f) {
            return Optional.ofNullable(fps.get(f));
        }

        @Override
        public List<SimilarEvent> findSameAmountBetween(long amount, Instant from, Instant to) {
            return similar.stream().filter(s -> amounts.get(s.eventId()) == amount
                    && !s.occurredAt().isBefore(from) && !s.occurredAt().isAfter(to)).toList();
        }
    }

    private static EventCandidate c(EventSource src, String ext, Instant at, Long amount, String sender, String text) {
        return new EventCandidate(src, src.name().toLowerCase(), ext, at, amount, "Nequi", sender, null, text);
    }

    @Test
    void mismoIdExternoEsDuplicado() {
        MemLookup l = new MemLookup();
        l.add(1, c(EventSource.GMAIL, "m-1", T, 50_000L, "Ana", "texto"));
        IdempotencyDecision d = new IdempotencyEngine(l).evaluate(c(EventSource.GMAIL, "m-1", T.plusSeconds(90), 50_000L, "Ana", "otro"));
        assertThat(d.outcome()).isEqualTo(IdempotencyDecision.Outcome.DUPLICADO);
        assertThat(d.matchedEventId()).isEqualTo(1L);
        assertThat(d.idempotencyKey()).isEqualTo("ext:GMAIL:m-1");
    }

    @Test
    void mismaHuellaSinIdExternoEsDuplicado() {
        MemLookup l = new MemLookup();
        l.add(7, c(EventSource.ANDROID_NEQUI, null, T, 50_000L, "Ana Pérez", "Ana Pérez te envió $50.000"));
        IdempotencyDecision d = new IdempotencyEngine(l).evaluate(
                c(EventSource.ANDROID_NEQUI, null, T.plusSeconds(10), 50_000L, "ana perez", "ANA PÉREZ  te envió $50.000"));
        assertThat(d.outcome()).isEqualTo(IdempotencyDecision.Outcome.DUPLICADO);
        assertThat(d.idempotencyKey()).startsWith("fp:");
    }

    @Test
    void mismoMontoYRemitenteDesdeOtraFuenteEsAmbiguo() {
        MemLookup l = new MemLookup();
        l.add(3, c(EventSource.ANDROID_NEQUI, "and-1", T, 80_000L, "Luis", "Luis te envió $80.000"));
        IdempotencyDecision d = new IdempotencyEngine(l, Duration.ofMinutes(10))
                .evaluate(c(EventSource.GMAIL, "gm-9", T.plusSeconds(120), 80_000L, "LUIS", "Recibiste $80.000 de Luis"));
        assertThat(d.outcome()).isEqualTo(IdempotencyDecision.Outcome.AMBIGUO);
        assertThat(d.matchedEventId()).isEqualTo(3L);
        assertThat(d.reason()).contains("#3");
    }

    @Test
    void remitenteDistintoOFueraDeVentanaEsNuevo() {
        MemLookup l = new MemLookup();
        l.add(3, c(EventSource.ANDROID_NEQUI, "and-1", T, 80_000L, "Luis", "x"));
        IdempotencyEngine e = new IdempotencyEngine(l, Duration.ofMinutes(10));
        assertThat(e.evaluate(c(EventSource.GMAIL, "g", T, 80_000L, "Marta", "y")).outcome())
                .isEqualTo(IdempotencyDecision.Outcome.NUEVO);
        assertThat(e.evaluate(c(EventSource.GMAIL, "g", T.plus(Duration.ofMinutes(30)), 80_000L, "Luis", "y")).outcome())
                .isEqualTo(IdempotencyDecision.Outcome.NUEVO);
    }

    @Test
    void mismaFuenteConIdsExternosDistintosEsNuevo() {
        MemLookup l = new MemLookup();
        l.add(3, c(EventSource.ANDROID_NEQUI, "and-1", T, 80_000L, "Luis", "x"));
        assertThat(new IdempotencyEngine(l).evaluate(c(EventSource.ANDROID_NEQUI, "and-2", T, 80_000L, "Luis", "x2")).outcome())
                .isEqualTo(IdempotencyDecision.Outcome.NUEVO);
    }

    @Test
    void huellaEsEstableYSensibleAlImporte() {
        EventCandidate a = c(EventSource.GMAIL, null, T, 1000L, "Ana", "hola");
        assertThat(Fingerprint.of(a)).isEqualTo(Fingerprint.of(a)).hasSize(64);
        assertThat(Fingerprint.of(a)).isNotEqualTo(Fingerprint.of(c(EventSource.GMAIL, null, T, 1001L, "Ana", "hola")));
    }
}
