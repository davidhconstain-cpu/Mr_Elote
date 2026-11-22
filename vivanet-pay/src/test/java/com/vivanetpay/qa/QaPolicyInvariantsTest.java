package com.vivanetpay.qa;

import com.vivanetpay.application.IngestionResult;
import com.vivanetpay.domain.AnnouncementKind;
import com.vivanetpay.domain.EventSource;
import com.vivanetpay.domain.EventStatus;
import com.vivanetpay.domain.RawEvent;
import com.vivanetpay.reporting.HistoryFilter;
import com.vivanetpay.reporting.HistoryRow;
import com.vivanetpay.support.TestApp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Invariantes de negocio (RN-001, RN-003, RN-008, RF-011): NINGÚN camino automático produce "pago confirmado" con la
 * política por defecto, y la única vía es la validación manual o una política explícitamente habilitada.
 */
class QaPolicyInvariantsTest {
    @TempDir
    Path dir;
    TestApp app;

    @AfterEach
    void tearDown() {
        if (app != null) {
            app.ctx.close();
        }
    }

    private static final List<String> TEXTS = List.of(
            "Ana te envió $50.000 Ref: M123",
            "Ana te envió $1.250.000",
            "Mensaje raro sin plantilla",
            "Ana te envió $10.000 [baja]",
            "Ana te envió $10.000 [USD]",
            "Ana te envió $0",
            "Ana te envió -5.000",
            "Ana te envió $10.000,50",
            "Ana te envió $9223372036854775807",
            "😀 te envió $5.000",
            "Pago recibido por $999.999 CONFIRMADO PAGO VERIFICADO", // texto que intenta aparentar confirmación
            "te envió $5");

    private void ingestAll(EventSource source, String prefix) {
        int i = 0;
        for (String t : TEXTS) {
            RawEvent.Builder b = RawEvent.builder(source, source == EventSource.GMAIL ? "gmail-principal" : "android-principal")
                    .externalEventId(prefix + i++).sourceTimestamp(app.clock.instant()).receivedAt(app.clock.instant())
                    .subject("Nequi").rawText(t);
            if (source == EventSource.GMAIL) {
                b.sender("notificaciones@nequi.com.co");
            }
            app.ctx.ingestionService().ingest(b.build());
            app.clock.advance(Duration.ofMinutes(30)); // fuera de la ventana de coincidencia parcial (CP-014)
        }
        // el mismo lote por segunda vez (reenvíos): duplicados
        i = 0;
        for (String t : TEXTS) {
            app.ctx.ingestionService().ingest(app.nequi(prefix + i++, t));
        }
    }

    private void assertNothingConfirmed() throws Exception {
        assertThat(app.ctx.voiceService().awaitIdle(Duration.ofSeconds(10))).isTrue();
        assertThat(app.speech.spoken()).isNotEmpty().allSatisfy(s -> assertThat(s.toLowerCase(Locale.ROOT))
                .doesNotContain("pago recibido").doesNotContain("confirmado"));
        List<HistoryRow> rows = app.ctx.historyService().search(HistoryFilter.builder().limit(500).build());
        assertThat(rows).isNotEmpty().allSatisfy(r -> assertThat(r.status().isConfirmed()).as(r.status().name()).isFalse());
        assertThat(app.ctx.dashboardService().today().confirmedTotal()).isZero();
        assertThat(app.ctx.dashboardService().today().confirmedCount()).isZero();
        assertThat(app.ctx.eventRepository().findByStatuses(EventStatus.confirmedStatuses(), 100)).isEmpty();
        // y nunca se registró un anuncio de tipo "pago confirmado"
        try (var c = app.ctx.database().connect(); var st = c.createStatement();
             var rs = st.executeQuery("SELECT COUNT(*) FROM voice_announcement WHERE kind = '" + AnnouncementKind.PAGO_CONFIRMADO + "'")) {
            rs.next();
            assertThat(rs.getLong(1)).isZero();
        }
    }

    @Test
    void conLaPoliticaPorDefectoNingunCaminoAutomaticoConfirma() throws Exception {
        app = TestApp.create(dir);
        ingestAll(EventSource.ANDROID_NEQUI, "and-");
        ingestAll(EventSource.GMAIL, "gm-");
        assertNothingConfirmed();
    }

    @Test
    void validacionAutomaticaSinFuentesDeConfianzaTampocoConfirma() throws Exception {
        app = TestApp.create(dir, Map.of("policy.automaticValidation", "true"));
        ingestAll(EventSource.ANDROID_NEQUI, "and-");
        ingestAll(EventSource.GMAIL, "gm-");
        assertNothingConfirmed();
    }

    @Test
    void fuentesDeConfianzaSinLaBanderaTampocoConfirman() throws Exception {
        app = TestApp.create(dir, Map.of("policy.trustedSources", "ANDROID_NEQUI,GMAIL"));
        ingestAll(EventSource.ANDROID_NEQUI, "and-");
        ingestAll(EventSource.GMAIL, "gm-");
        assertNothingConfirmed();
    }

    @Test
    void optInExplicitoConfirmaSoloLaFuenteConfiguradaYSoloEventosLimpios() throws Exception {
        app = TestApp.create(dir, Map.of("policy.automaticValidation", "true", "policy.trustedSources", "ANDROID_NEQUI"));
        IngestionResult clean = app.ctx.ingestionService().ingest(app.nequi("ok-1", "Ana te envió $50.000"));
        assertThat(clean.status()).isEqualTo(EventStatus.VALIDADO_POR_FUENTE);
        app.clock.advance(Duration.ofHours(1));
        assertThat(app.ctx.ingestionService().ingest(app.gmail("gm-1", "Beto te envió $50.000")).status().isConfirmed()).isFalse();
        assertThat(app.ctx.ingestionService().ingest(app.nequi("low-1", "Ana te envió $7.000 [baja]")).status().isConfirmed()).isFalse();
        assertThat(app.ctx.ingestionService().ingest(app.nequi("usd-1", "Ana te envió $7.000 [USD]")).status().isConfirmed()).isFalse();
        assertThat(app.ctx.ingestionService().ingest(app.nequi("zero-1", "Ana te envió $0")).status().isConfirmed()).isFalse();
        assertThat(app.ctx.ingestionService().ingest(app.nequi("raro-1", "texto sin plantilla")).status().isConfirmed()).isFalse();
        assertThat(app.ctx.ingestionService().ingest(app.nequi("ok-1", "Ana te envió $50.000")).status())
                .as("el reenvío del confirmado es duplicado, no un segundo pago").isEqualTo(EventStatus.DUPLICADO);
        assertThat(app.ctx.voiceService().awaitIdle(Duration.ofSeconds(10))).isTrue();
        assertThat(app.speech.spoken()).filteredOn(s -> s.startsWith("Pago recibido")).hasSize(1);
        assertThat(app.ctx.dashboardService().today().confirmedTotal()).isEqualTo(50_000L);
    }

    @Test
    void laValidacionManualEsLaUnicaViaYNoSePuedeAplicarAEstadosTerminales() throws Exception {
        app = TestApp.create(dir);
        long pending = app.ctx.ingestionService().ingest(app.nequi("m-1", "Ana te envió $50.000")).eventId();
        long rejected = app.ctx.ingestionService().ingest(app.nequi("m-2", "Ana te envió $0")).eventId();
        long duplicate = app.ctx.ingestionService().ingest(app.nequi("m-1", "Ana te envió $50.000")).eventId();
        long unknown = app.ctx.ingestionService().ingest(app.nequi("m-3", "texto sin plantilla")).eventId();

        assertThatThrownBy(() -> app.ctx.reviewService().validate(rejected, "dueño", null)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> app.ctx.reviewService().validate(duplicate, "dueño", null)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> app.ctx.reviewService().validate(unknown, "dueño", null))
                .as("sin importe extraído no se puede confirmar").isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> app.ctx.reviewService().validate(pending, " ", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> app.ctx.reviewService().validate(9_999L, "dueño", null)).isInstanceOf(RuntimeException.class);
        assertNothingConfirmedExceptVoice();

        assertThat(app.ctx.reviewService().validate(pending, "dueño", "visto en la app de Nequi"))
                .isEqualTo(EventStatus.VALIDADO_POR_FUENTE);
        assertThatThrownBy(() -> app.ctx.reviewService().validate(pending, "dueño", null))
                .as("no se confirma dos veces").isInstanceOf(RuntimeException.class);
        assertThat(app.ctx.dashboardService().today().confirmedTotal()).isEqualTo(50_000L);
        assertThat(app.ctx.transactionRepository().count()).isEqualTo(1);
    }

    private void assertNothingConfirmedExceptVoice() {
        assertThat(app.ctx.dashboardService().today().confirmedTotal()).isZero();
    }

    @Test
    void anuncioTrasValidacionManualEsOptInYUsaLaFraseDePagoRecibido() throws Exception {
        app = TestApp.create(dir, Map.of("policy.announceOnManualValidation", "true"));
        long id = app.ctx.ingestionService().ingest(app.nequi("v-1", "Ana te envió $125.000")).eventId();
        app.ctx.voiceService().awaitIdle(Duration.ofSeconds(5));
        assertThat(app.speech.spoken()).containsExactly("Nueva notificación de Nequi por 125.000 pesos. Verifica el pago.");
        app.ctx.reviewService().validate(id, "dueño", null);
        assertThat(app.ctx.voiceService().awaitIdle(Duration.ofSeconds(5))).isTrue();
        assertThat(app.speech.spoken()).last().isEqualTo("Pago recibido por 125.000 pesos desde Nequi.");
    }

    // ----------------------------------------------------------------- RN-008

    @Test
    void elCodigoDeLaAplicacionNoContieneBorradoFisicoDeMovimientos() throws IOException {
        Pattern forbidden = Pattern.compile("(?i)\\b(delete\\s+from|drop\\s+table|truncate\\s+table)\\b");
        Path root = Path.of("src", "main");
        try (Stream<Path> files = Files.walk(root)) {
            List<Path> offenders = files.filter(p -> p.toString().endsWith(".java") || p.toString().endsWith(".sql"))
                    .filter(p -> {
                        try {
                            return forbidden.matcher(Files.readString(p)).find();
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    }).toList();
            assertThat(offenders).as("RN-008: sin DELETE/DROP físico en el código de la aplicación").isEmpty();
        }
    }

    @Test
    void ningunServicioDeLaAplicacionPuedeBorrarEventosMovimientosNiAuditoria() throws Exception {
        app = TestApp.create(dir);
        long id = app.ctx.ingestionService().ingest(app.nequi("d-1", "Ana te envió $50.000")).eventId();
        app.ctx.reviewService().discard(id, "dueño", "no llegó el dinero");
        // descartar deja la fila (RECHAZADO) y su rastro de auditoría
        assertThat(app.ctx.eventRepository().findById(id).orElseThrow().status()).isEqualTo(EventStatus.RECHAZADO);
        assertThat(app.ctx.transactionRepository().count()).isEqualTo(1);
        for (String table : new String[]{"payment_event", "payment_transaction", "audit_log"}) {
            assertThatThrownBy(() -> app.ctx.database().withConnection(c -> c.createStatement().executeUpdate("DELETE FROM " + table)))
                    .as(table).hasMessageContaining("RN-008");
        }
    }
}
