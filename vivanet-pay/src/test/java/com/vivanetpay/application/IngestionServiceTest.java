package com.vivanetpay.application;

import com.vivanetpay.domain.AnnouncementKind;
import com.vivanetpay.domain.AuditEntry;
import com.vivanetpay.domain.EventStatus;
import com.vivanetpay.domain.RawEvent;
import com.vivanetpay.support.TestApp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class IngestionServiceTest {
    @TempDir
    Path dir;
    TestApp app;

    @BeforeEach
    void setUp() {
        app = TestApp.create(dir);
    }

    @AfterEach
    void tearDown() {
        app.ctx.close();
    }

    @Test
    void eventoReconocidoQuedaParseadoConMovimientoYAuditoria() {
        List<AppEvent> published = new CopyOnWriteArrayList<>();
        app.ctx.eventBus().subscribeAll(published::add);

        IngestionResult r = app.ctx.ingestionService().ingest(app.nequi("n-1", "Ana Pérez te envió $50.000 Ref: M123"));

        assertThat(r.status()).isEqualTo(EventStatus.PARSEADO);
        assertThat(r.transactionId()).isNotNull();
        var tx = app.ctx.transactionRepository().findByEventId(r.eventId()).orElseThrow();
        assertThat(tx.amount()).isEqualTo(50_000L);
        assertThat(tx.currency()).isEqualTo("COP");
        assertThat(tx.sender()).isEqualTo("Ana Pérez");
        assertThat(tx.reference()).isEqualTo("M123");
        assertThat(app.ctx.auditRepository().findByEntity(AuditEntry.ENTITY_EVENT, r.eventId().toString()))
                .extracting(AuditEntry::afterValue).containsExactly("PARSEADO", "PARSEADO");
        assertThat(app.ctx.validationRepository().findByEvent(r.eventId())).isNotEmpty();
        assertThat(published).hasAtLeastOneElementOfType(AppEvent.TransactionRecorded.class)
                .hasAtLeastOneElementOfType(AppEvent.EventNeedsReview.class);
    }

    @Test
    void reintentoConMismoIdExternoNoDuplicaMovimiento() {
        RawEvent e = app.nequi("n-1", "Ana te envió $50.000");
        IngestionResult first = app.ctx.ingestionService().ingest(e);
        IngestionResult second = app.ctx.ingestionService().ingest(app.nequi("n-1", "Ana te envió $50.000"));

        assertThat(second.status()).isEqualTo(EventStatus.DUPLICADO);
        assertThat(second.matchedEventId()).isEqualTo(first.eventId());
        assertThat(second.transactionId()).isNull();
        assertThat(app.ctx.transactionRepository().count()).isEqualTo(1);
        assertThat(app.ctx.eventRepository().count()).as("el duplicado se conserva como evento (RF-006)").isEqualTo(2);
    }

    @Test
    void sinIdExternoDeduplicaPorHuella() {
        IngestionResult a = app.ctx.ingestionService().ingest(app.nequi(null, "Ana te envió $20.000"));
        IngestionResult b = app.ctx.ingestionService().ingest(app.nequi(null, "Ana te envió $20.000"));
        assertThat(a.status()).isEqualTo(EventStatus.PARSEADO);
        assertThat(b.status()).isEqualTo(EventStatus.DUPLICADO);
        assertThat(app.ctx.transactionRepository().count()).isEqualTo(1);
    }

    @Test
    void mismoPagoPorGmailYAndroidVaARevisionCP014() {
        IngestionResult android = app.ctx.ingestionService().ingest(app.nequi("and-1", "Luis te envió $80.000"));
        app.clock.advance(Duration.ofMinutes(2));
        IngestionResult gmail = app.ctx.ingestionService().ingest(app.gmail("gm-1", "Luis te envió $80.000 (correo)"));
        assertThat(gmail.status()).isEqualTo(EventStatus.PENDIENTE_REVISION);
        assertThat(gmail.matchedEventId()).isEqualTo(android.eventId());
        assertThat(gmail.reason()).contains("Posible duplicado");
        assertThat(app.ctx.eventRepository().findById(gmail.eventId()).orElseThrow().possibleDuplicateOfEventId())
                .isEqualTo(android.eventId());
    }

    @Test
    void formatoDesconocidoQuedaPendienteYNoSeAnunciaConfirmadoCP006() throws Exception {
        IngestionResult r = app.ctx.ingestionService().ingest(app.nequi("x-1", "Mensaje raro sin plantilla"));
        assertThat(r.status()).isEqualTo(EventStatus.PENDIENTE_REVISION);
        assertThat(r.transactionId()).isNull();
        assertThat(r.announcement()).isNotEqualTo(AnnouncementKind.PAGO_CONFIRMADO);
        app.ctx.voiceService().awaitIdle(Duration.ofSeconds(5));
        assertThat(app.speech.spoken()).allSatisfy(s -> assertThat(s).doesNotContain("Pago recibido"));
    }

    @Test
    void importeCeroNegativoOMonedaExtranjeraSeRechazanCP010() {
        assertThat(app.ctx.ingestionService().ingest(app.nequi("z-1", "Ana te envió $0")).status())
                .isEqualTo(EventStatus.RECHAZADO);
        assertThat(app.ctx.ingestionService().ingest(app.nequi("z-2", "Ana te envió -5.000")).status())
                .isEqualTo(EventStatus.RECHAZADO);
        assertThat(app.ctx.ingestionService().ingest(app.nequi("z-3", "Ana te envió $10.000 [USD]")).status())
                .isEqualTo(EventStatus.RECHAZADO);
        assertThat(app.ctx.ingestionService().ingest(app.nequi("z-4", "Ana te envió $10.000,50")).status())
                .as("importe con centavos no inequívoco => revisión, no contabilización silenciosa")
                .isEqualTo(EventStatus.PENDIENTE_REVISION);
        assertThat(app.ctx.transactionRepository().count()).isZero();
    }

    @Test
    void politicaConservadoraAnunciaSoloPosiblePago() throws Exception {
        app.ctx.ingestionService().ingest(app.nequi("v-1", "Ana te envió $125.000"));
        assertThat(app.ctx.voiceService().awaitIdle(Duration.ofSeconds(5))).isTrue();
        assertThat(app.speech.spoken()).containsExactly("Nueva notificación de Nequi por 125.000 pesos. Verifica el pago.");
        var history = app.ctx.historyService().search(com.vivanetpay.reporting.HistoryFilter.all());
        assertThat(history).allSatisfy(row -> assertThat(row.status().isConfirmed()).isFalse());
    }

    @Test
    void eventoHistoricoNoSeAnuncia() throws Exception {
        RawEvent historical = RawEvent.builder(com.vivanetpay.domain.EventSource.GMAIL, "gmail-principal")
                .externalEventId("old-1").receivedAt(app.clock.instant()).rawText("Ana te envió $5.000")
                .historical(true).build();
        app.ctx.ingestionService().ingest(historical);
        app.ctx.voiceService().awaitIdle(Duration.ofSeconds(5));
        assertThat(app.speech.spoken()).isEmpty();
    }

    @Test
    void duplicadoNoSeAnuncia() throws Exception {
        app.ctx.ingestionService().ingest(app.nequi("d-1", "Ana te envió $5.000"));
        app.ctx.ingestionService().ingest(app.nequi("d-1", "Ana te envió $5.000"));
        app.ctx.voiceService().awaitIdle(Duration.ofSeconds(5));
        assertThat(app.speech.spoken()).hasSize(1);
    }
}
