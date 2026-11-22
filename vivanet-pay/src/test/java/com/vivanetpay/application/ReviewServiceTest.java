package com.vivanetpay.application;

import com.vivanetpay.domain.AuditEntry;
import com.vivanetpay.domain.EventStatus;
import com.vivanetpay.domain.Validation;
import com.vivanetpay.support.TestApp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Flujo D: revisión manual con auditoría (RF-023, RF-024). */
class ReviewServiceTest {
    @TempDir
    Path dir;
    TestApp app;

    @BeforeEach
    void setUp() {
        app = TestApp.create(dir, Map.of("policy.announceOnManualValidation", "true"));
    }

    @AfterEach
    void tearDown() {
        app.ctx.close();
    }

    @Test
    void validarManualmenteConfirmaYAudita() throws Exception {
        long id = app.ctx.ingestionService().ingest(app.nequi("r-1", "Ana te envió $70.000")).eventId();
        String rawBefore = app.ctx.eventRepository().findById(id).orElseThrow().rawText();
        assertThat(app.ctx.reviewService().pending(10)).extracting(i -> i.row().eventId()).contains(id);

        app.ctx.reviewService().validate(id, "dueño", "Verificado en app Nequi");

        var event = app.ctx.eventRepository().findById(id).orElseThrow();
        assertThat(event.rawText()).isEqualTo(rawBefore);
        assertThat(app.ctx.validationRepository().findByEvent(id))
                .filteredOn(v -> v.rule().equals(ReviewService.RULE_MANUAL_VALIDATE))
                .singleElement().extracting(Validation::actor).isEqualTo("dueño");
        assertThat(app.ctx.auditRepository().findByEntity(AuditEntry.ENTITY_EVENT, Long.toString(id)))
                .anySatisfy(a -> {
                    assertThat(a.actor()).isEqualTo("dueño");
                    assertThat(a.beforeValue()).isEqualTo("PARSEADO");
                    assertThat(a.afterValue()).isEqualTo("VALIDADO_POR_FUENTE");
                    assertThat(a.reason()).isEqualTo("Verificado en app Nequi");
                });
        // con announceOnManualValidation=true se anuncia "pago confirmado" y pasa a ANUNCIADO
        app.ctx.voiceService().awaitIdle(Duration.ofSeconds(5));
        assertThat(app.speech.spoken()).contains("Pago recibido por 70.000 pesos desde Nequi.");
        assertThat(app.ctx.eventRepository().findById(id).orElseThrow().status()).isEqualTo(EventStatus.ANUNCIADO);
        assertThat(app.ctx.reviewService().pending(10)).extracting(i -> i.row().eventId()).doesNotContain(id);
    }

    @Test
    void descartarExigeMotivoYNoBorra() {
        long id = app.ctx.ingestionService().ingest(app.nequi("r-2", "Ana te envió $70.000")).eventId();
        assertThatThrownBy(() -> app.ctx.reviewService().discard(id, "dueño", " "))
                .isInstanceOf(IllegalArgumentException.class);
        app.ctx.reviewService().discard(id, "dueño", "No aparece en Nequi");
        assertThat(app.ctx.eventRepository().findById(id).orElseThrow().status()).isEqualTo(EventStatus.RECHAZADO);
        assertThat(app.ctx.transactionRepository().findByEventId(id)).isPresent();
    }

    @Test
    void mantenerPendienteSoloDejaConstancia() {
        long id = app.ctx.ingestionService().ingest(app.nequi("r-3", "Ana te envió $70.000")).eventId();
        app.ctx.reviewService().keepPending(id, "cajero", "Esperando extracto");
        assertThat(app.ctx.eventRepository().findById(id).orElseThrow().status()).isEqualTo(EventStatus.PARSEADO);
        assertThat(app.ctx.auditRepository().findByEntity(AuditEntry.ENTITY_EVENT, Long.toString(id)))
                .anySatisfy(a -> assertThat(a.action()).isEqualTo(AuditEntry.ACTION_REVIEW_KEEP));
    }

    @Test
    void reglasDeRevision() {
        long dup = app.ctx.ingestionService().ingest(app.nequi("r-4", "Ana te envió $1.000")).eventId();
        long dup2 = app.ctx.ingestionService().ingest(app.nequi("r-4", "Ana te envió $1.000")).eventId();
        assertThatThrownBy(() -> app.ctx.reviewService().validate(dup2, "dueño", null))
                .as("un DUPLICADO no tiene movimiento y no puede confirmarse").isInstanceOf(IllegalStateException.class);
        long noAmount = app.ctx.ingestionService().ingest(app.nequi("r-5", "Texto desconocido")).eventId();
        assertThatThrownBy(() -> app.ctx.reviewService().validate(noAmount, "dueño", null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> app.ctx.reviewService().validate(dup, "", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
