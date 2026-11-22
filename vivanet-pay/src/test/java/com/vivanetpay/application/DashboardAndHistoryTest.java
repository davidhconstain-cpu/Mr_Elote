package com.vivanetpay.application;

import com.vivanetpay.domain.EventSource;
import com.vivanetpay.domain.EventStatus;
import com.vivanetpay.domain.RawEvent;
import com.vivanetpay.reporting.DashboardSummary;
import com.vivanetpay.reporting.HistoryFilter;
import com.vivanetpay.reporting.HistoryRow;
import com.vivanetpay.support.TestApp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardAndHistoryTest {
    @TempDir
    Path dir;
    TestApp app;

    @BeforeEach
    void setUp() {
        // "Ahora" = 2026-11-22 00:30 en Bogotá (05:30Z)
        app = TestApp.create(dir, Map.of(), Instant.parse("2026-11-22T05:30:00Z"));
    }

    @AfterEach
    void tearDown() {
        app.ctx.close();
    }

    private RawEvent at(String id, EventSource src, String instant, String text) {
        Instant t = Instant.parse(instant);
        return RawEvent.builder(src, src == EventSource.GMAIL ? "gmail-principal" : "android-principal")
                .externalEventId(id).sourceTimestamp(t).receivedAt(t).rawText(text).build();
    }

    @Test
    void eventoCercaDeMedianocheSeAsignaAlDiaLocalDeColombiaCP015() {
        // 23:59 del 21-nov en Bogotá (04:59Z del 22) => NO es "hoy"
        app.ctx.ingestionService().ingest(at("a", EventSource.ANDROID_NEQUI, "2026-11-22T04:59:00Z", "Ana te envió $10.000"));
        // 00:01 del 22-nov en Bogotá (05:01Z) => SÍ es "hoy"
        app.ctx.ingestionService().ingest(at("b", EventSource.ANDROID_NEQUI, "2026-11-22T05:01:00Z", "Beto te envió $25.000"));

        DashboardSummary today = app.ctx.dashboardService().today();
        assertThat(today.day()).isEqualTo(LocalDate.of(2026, 11, 22));
        assertThat(today.unconfirmedTotal()).isEqualTo(25_000L);
        assertThat(today.unconfirmedCount()).isEqualTo(1);
        assertThat(today.confirmedTotal()).isZero();
        assertThat(today.lastMovement().sender()).isEqualTo("Beto");

        DashboardSummary yesterday = app.ctx.dashboardService().forDay(LocalDate.of(2026, 11, 21));
        assertThat(yesterday.unconfirmedTotal()).isEqualTo(10_000L);
    }

    @Test
    void tableroSeparaConfirmadosYExcluyeDuplicadosYRechazados() {
        long a = app.ctx.ingestionService().ingest(at("1", EventSource.ANDROID_NEQUI, "2026-11-22T05:10:00Z", "Ana te envió $10.000")).eventId();
        app.ctx.ingestionService().ingest(at("1", EventSource.ANDROID_NEQUI, "2026-11-22T05:10:00Z", "Ana te envió $10.000"));
        app.ctx.ingestionService().ingest(at("2", EventSource.ANDROID_NEQUI, "2026-11-22T05:11:00Z", "Beto te envió $0"));
        app.ctx.ingestionService().ingest(at("3", EventSource.GMAIL, "2026-11-22T05:12:00Z", "Carla te envió $5.000"));
        app.ctx.reviewService().validate(a, "dueño", "verificado");

        DashboardSummary s = app.ctx.dashboardService().today();
        assertThat(s.confirmedTotal()).isEqualTo(10_000L);
        assertThat(s.confirmedCount()).isEqualTo(1);
        assertThat(s.unconfirmedTotal()).isEqualTo(5_000L);
        assertThat(s.countAll()).isEqualTo(2);
        assertThat(s.byBank()).singleElement().satisfies(b -> {
            assertThat(b.bank()).isEqualTo("Nequi");
            assertThat(b.confirmedTotal()).isEqualTo(10_000L);
            assertThat(b.unconfirmedTotal()).isEqualTo(5_000L);
        });
    }

    @Test
    void historialConFiltros() {
        app.ctx.ingestionService().ingest(at("1", EventSource.ANDROID_NEQUI, "2026-11-20T15:00:00Z", "Ana te envió $10.000"));
        app.ctx.ingestionService().ingest(at("2", EventSource.GMAIL, "2026-11-21T15:00:00Z", "Beto te envió $50.000 Ref: ZX9"));
        app.ctx.ingestionService().ingest(at("3", EventSource.ANDROID_NEQUI, "2026-11-22T05:20:00Z", "Mensaje desconocido"));

        HistoryService h = app.ctx.historyService();
        assertThat(h.search(HistoryFilter.all())).hasSize(3);
        assertThat(h.search(HistoryFilter.builder().sources(Set.of(EventSource.GMAIL)).build()))
                .singleElement().extracting(HistoryRow::sender).isEqualTo("Beto");
        assertThat(h.search(HistoryFilter.builder().statuses(Set.of(EventStatus.PENDIENTE_REVISION)).build())).hasSize(1);
        assertThat(h.search(HistoryFilter.builder().minAmount(20_000L).maxAmount(60_000L).build()))
                .singleElement().extracting(HistoryRow::amount).isEqualTo(50_000L);
        assertThat(h.search(HistoryFilter.builder().from(LocalDate.of(2026, 11, 21)).to(LocalDate.of(2026, 11, 21)).build()))
                .singleElement().extracting(HistoryRow::formattedAmount).isEqualTo("$50.000");
        assertThat(h.search(HistoryFilter.builder().text("zx9").build())).hasSize(1);
        assertThat(h.search(HistoryFilter.builder().bank("nequi").build())).hasSize(2);
        assertThat(h.search(HistoryFilter.builder().limit(1).build())).hasSize(1);
    }
}
