package com.vivanetpay.qa;

import com.vivanetpay.app.AppConfig;
import com.vivanetpay.app.AppContext;
import com.vivanetpay.app.Bootstrap;
import com.vivanetpay.app.FakeConnectorProvider;
import com.vivanetpay.domain.ConnectorStatus;
import com.vivanetpay.domain.EventSource;
import com.vivanetpay.domain.EventStatus;
import com.vivanetpay.domain.RawEvent;
import com.vivanetpay.reporting.CsvExporter;
import com.vivanetpay.reporting.DashboardSummary;
import com.vivanetpay.reporting.HistoryFilter;
import com.vivanetpay.support.FakeNequiParser;
import com.vivanetpay.support.MutableClock;
import com.vivanetpay.support.TestApp;
import com.vivanetpay.voice.LoggingSpeechEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

/** CP-015 (medianoche en Colombia, con cualquier zona horaria del equipo) y CP-013 (sin Internet). */
class QaTimeAndOutageTest {
    @TempDir
    Path dir;
    private TimeZone originalZone;

    @BeforeEach
    void saveZone() {
        originalZone = TimeZone.getDefault();
    }

    @AfterEach
    void restoreZone() {
        TimeZone.setDefault(originalZone);
    }

    private static RawEvent at(String id, String instant, String text) {
        Instant t = Instant.parse(instant);
        return RawEvent.builder(EventSource.ANDROID_NEQUI, "android-principal").externalEventId(id)
                .sourceTimestamp(t).receivedAt(t).rawText(text).build();
    }

    /** El "día" del tablero, el filtro del historial y el CSV deben ser SIEMPRE de Colombia, sin importar la zona del PC. */
    @Test
    void medianocheDeColombiaEsIndependienteDeLaZonaHorariaDelEquipo() {
        for (String zone : new String[]{"UTC", "Asia/Tokyo", "Pacific/Kiritimati", "America/Los_Angeles", "Pacific/Pago_Pago"}) {
            TimeZone.setDefault(TimeZone.getTimeZone(zone));
            Path d = dir.resolve(zone.replace('/', '_'));
            // "ahora" = 22-nov 00:30 en Bogotá (05:30Z)
            TestApp app = TestApp.create(d, Map.of(), Instant.parse("2026-11-22T05:30:00Z"));
            try {
                var ing = app.ctx.ingestionService();
                ing.ingest(at("a", "2026-11-22T04:59:59Z", "Ana te envió $1.000"));      // 21-nov 23:59:59 Bogotá
                ing.ingest(at("b", "2026-11-22T05:00:00Z", "Beto te envió $2.000"));     // 22-nov 00:00:00 Bogotá
                ing.ingest(at("c", "2026-11-23T04:59:59Z", "Carla te envió $4.000"));    // 22-nov 23:59:59 Bogotá
                ing.ingest(at("d", "2026-11-23T05:00:00Z", "Dora te envió $8.000"));     // 23-nov 00:00:00 Bogotá

                DashboardSummary hoy = app.ctx.dashboardService().forDay(LocalDate.of(2026, 11, 22));
                assertThat(hoy.unconfirmedTotal()).as(zone).isEqualTo(6_000L);
                assertThat(app.ctx.dashboardService().forDay(LocalDate.of(2026, 11, 21)).unconfirmedTotal()).as(zone).isEqualTo(1_000L);
                assertThat(app.ctx.dashboardService().forDay(LocalDate.of(2026, 11, 23)).unconfirmedTotal()).as(zone).isEqualTo(8_000L);
                assertThat(app.ctx.dashboardService().today().day()).as(zone).isEqualTo(LocalDate.of(2026, 11, 22));

                var rows = app.ctx.historyService().search(HistoryFilter.builder()
                        .from(LocalDate.of(2026, 11, 22)).to(LocalDate.of(2026, 11, 22)).build());
                assertThat(rows).as(zone).extracting(r -> r.sender()).containsExactlyInAnyOrder("Beto", "Carla");

                String csv = CsvExporter.toString(app.ctx.historyService().search(HistoryFilter.all()));
                assertThat(csv).as(zone).contains("21/11/2026 23:59:59").contains("22/11/2026 00:00:00")
                        .contains("22/11/2026 23:59:59").contains("23/11/2026 00:00:00");
                assertThat(rows.get(0).formattedOccurredAt()).as(zone).contains("/11/2026");
            } finally {
                app.ctx.close();
            }
        }
    }

    @Test
    void cambioDeDiaMientrasLaAplicacionSigueAbiertaMueveElTablero() {
        TestApp app = TestApp.create(dir.resolve("rollover"), Map.of(), Instant.parse("2026-11-23T04:59:00Z")); // 23:59 del 22
        try {
            app.ctx.ingestionService().ingest(at("r1", "2026-11-23T04:58:00Z", "Ana te envió $3.000"));
            assertThat(app.ctx.dashboardService().today().unconfirmedTotal()).isEqualTo(3_000L);
            app.clock.set(Instant.parse("2026-11-23T05:01:00Z")); // ya es 23-nov en Bogotá
            assertThat(app.ctx.dashboardService().today().day()).isEqualTo(LocalDate.of(2026, 11, 23));
            assertThat(app.ctx.dashboardService().today().unconfirmedTotal()).isZero();
        } finally {
            app.ctx.close();
        }
    }

    // ----------------------------------------------------------------- CP-013

    @Test
    void sinInternetElHistorialLocalSigueDisponibleYElConectorQuedaDesconectado() throws Exception {
        AppConfig config = AppConfig.of(dir.resolve("offline"), Map.of("connectors.enabled", "android"));
        MutableClock clock = new MutableClock(Instant.parse("2026-11-22T15:00:00Z"));
        Bootstrap.Result r = Bootstrap.start(AppContext.builder(config).clock(clock).speechEngine(new LoggingSpeechEngine())
                .parser(new FakeNequiParser()));
        AppContext ctx = r.context();
        try {
            var connector = (FakeConnectorProvider.FakeConnector) ctx.connectorRegistry().get("android-principal").orElseThrow();
            connector.receive("off-1", "Ana te envió $15.000");
            connector.receive("off-2", "Beto te envió $25.000");
            connector.fail("Sin red");
            assertThat(ctx.connectorRepository().find("android-principal").orElseThrow().status())
                    .isEqualTo(ConnectorStatus.DESCONECTADO);
            // con el conector caído se sigue consultando todo lo guardado
            assertThat(ctx.dashboardService().today().unconfirmedTotal()).isEqualTo(40_000L);
            assertThat(ctx.historyService().search(HistoryFilter.all())).hasSize(2);
            assertThat(ctx.reviewService().pending(10)).hasSize(2);
            assertThat(ctx.reviewService().validate(ctx.eventRepository().findByStatuses(EventStatus.unconfirmedStatuses(), 1)
                    .get(0).id(), "dueño", null)).isEqualTo(EventStatus.VALIDADO_POR_FUENTE);
        } finally {
            ctx.close();
        }
        // reinicio con la red aún caída: los datos siguen allí (RN-010)
        Bootstrap.Result again = Bootstrap.start(AppContext.builder(config).clock(clock)
                .speechEngine(new LoggingSpeechEngine()).parser(new FakeNequiParser()));
        try {
            assertThat(again.context().transactionRepository().count()).isEqualTo(2);
            assertThat(again.context().connectorRepository().loadCursor("android-principal")).contains("off-2");
        } finally {
            again.context().close();
        }
    }
}
