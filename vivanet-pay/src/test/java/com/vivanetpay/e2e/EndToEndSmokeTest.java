package com.vivanetpay.e2e;

import com.vivanetpay.app.AppConfig;
import com.vivanetpay.app.AppContext;
import com.vivanetpay.app.Bootstrap;
import com.vivanetpay.domain.ConnectorStatus;
import com.vivanetpay.domain.EventSource;
import com.vivanetpay.domain.EventStatus;
import com.vivanetpay.domain.RawEvent;
import com.vivanetpay.integration.ConnectorDependencies;
import com.vivanetpay.integration.android.AndroidWebhookConnector;
import com.vivanetpay.integration.android.AndroidWebhookConnectorProvider;
import com.vivanetpay.integration.android.WebhookSigner;
import com.vivanetpay.reporting.DashboardSummary;
import com.vivanetpay.voice.LoggingSpeechEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de humo de extremo a extremo (Linux, sin UI): contexto completo con base temporal, parsers REALES (por defecto
 * de AppContext), receptor Android REAL en puerto efímero, voz simulada y política conservadora (DP-01).
 */
class EndToEndSmokeTest {
    private static final String NEQUI_TEXT = "JUAN PÉREZ te envió $50.000";

    @TempDir
    Path dir;

    private AppContext ctx;
    private AndroidWebhookConnector android;
    private LoggingSpeechEngine speech;
    private final HttpClient http = HttpClient.newBuilder().proxy(HttpClient.Builder.NO_PROXY)
            .connectTimeout(Duration.ofSeconds(5)).build();

    @AfterEach
    void tearDown() {
        if (ctx != null) {
            ctx.close(); // detiene también el conector Android
        }
    }

    /** Arranque completo (Bootstrap) sin conectores por SPI; el Android real se registra con puerto efímero. */
    private void boot(String authMode) {
        boot(authMode, false);
    }

    /** @param automatic true = política por defecto (DP-01: confirmación automática); false = modo conservador. */
    private void boot(String authMode, boolean automatic) {
        speech = new LoggingSpeechEngine();
        AppConfig config = AppConfig.of(dir, Map.of("backup.onStart", "false", "connectors.enabled", "none",
                "policy.automaticValidation", Boolean.toString(automatic)));
        Bootstrap.Result r = Bootstrap.start(AppContext.builder(config).clock(Clock.systemUTC()).speechEngine(speech));
        ctx = r.context();
        assertThat(r.databaseHealthy()).isTrue();
        assertThat(ctx.parserRegistry().parsers()).as("parsers reales por defecto").hasSize(3);
        android = (AndroidWebhookConnector) new AndroidWebhookConnectorProvider().create(new ConnectorDependencies(
                "android-principal", ctx.ingestionService(), ctx.connectorRepository(), ctx.secretStore(),
                Map.of("port", "0", "auth", authMode), dir, ctx.clock()));
        ctx.connectorRegistry().register(android);
        android.start();
        assertThat(android.isRunning()).as(android.state().detail()).isTrue();
        assertThat(android.state().status()).isEqualTo(ConnectorStatus.CONECTADO);
    }

    private URI events() {
        return android.endpoints().get(0);
    }

    private static String json(String eventId, String text) {
        return """
                {"schemaVersion":1,"eventId":"%s","source":"ANDROID_NEQUI","app":"com.nequi.MobileApp",\
                "title":"Nequi","text":"%s","capturedAt":"%d","device":"Celular caja"}"""
                .formatted(eventId, text, System.currentTimeMillis() / 1000);
    }

    private HttpResponse<String> postToken(String body, String token) throws Exception {
        return http.send(HttpRequest.newBuilder(events()).header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postHmac(String body, byte[] secret) throws Exception {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        String ts = Long.toString(ctx.clock().instant().getEpochSecond());
        String nonce = UUID.randomUUID().toString().replace("-", "");
        return http.send(HttpRequest.newBuilder(events()).header("Content-Type", "application/json")
                .header(WebhookSigner.HEADER_TIMESTAMP, ts).header(WebhookSigner.HEADER_NONCE, nonce)
                .header(WebhookSigner.HEADER_SIGNATURE, WebhookSigner.signatureHeader(secret, ts, nonce, b))
                .POST(HttpRequest.BodyPublishers.ofByteArray(b)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private void assertUnconfirmed50k(DashboardSummary s) {
        assertThat(s.unconfirmedTotal()).isEqualTo(50_000L);
        assertThat(s.unconfirmedCount()).isEqualTo(1);
        assertThat(s.confirmedTotal()).isZero();
    }

    @Test
    void modoTokenNequiRealisticoDeExtremoAExtremo() throws Exception {
        boot("token");
        String token = new String(android.revealSecret());

        HttpResponse<String> r = postToken(json("md-1-1", NEQUI_TEXT), token);
        assertThat(r.statusCode()).isEqualTo(202);

        // transacción persistida SIN confirmar (modo conservador: policy.automaticValidation=false)
        assertThat(ctx.transactionRepository().count()).isEqualTo(1);
        var rows = ctx.reportingQueries().row(1L).orElseThrow();
        assertThat(rows.status()).isEqualTo(EventStatus.PARSEADO);
        assertThat(rows.status().isConfirmed()).isFalse();
        assertThat(rows.amount()).isEqualTo(50_000L);
        assertThat(rows.sender()).containsIgnoringCase("JUAN");

        // anuncio de posible pago, nunca "pago recibido"
        ctx.voiceService().awaitIdle(Duration.ofSeconds(5));
        assertThat(speech.spoken()).singleElement().satisfies(t -> {
            assertThat(t).contains("Verifica el pago").contains("50.000");
            assertThat(t).doesNotContain("Pago recibido");
        });

        // tablero: $50.000 por verificar
        assertUnconfirmed50k(ctx.dashboardService().today());

        // reenvío del mismo eventId => sin duplicado ni segundo anuncio
        HttpResponse<String> dup = postToken(json("md-1-1", NEQUI_TEXT), token);
        assertThat(dup.statusCode()).isEqualTo(200);
        assertThat(dup.body()).contains("duplicate");
        assertThat(ctx.transactionRepository().count()).isEqualTo(1);
        assertUnconfirmed50k(ctx.dashboardService().today());

        // token inválido => 401 y sin pago
        assertThat(postToken(json("md-1-2", NEQUI_TEXT), "token-falso").statusCode()).isEqualTo(401);
        assertThat(ctx.transactionRepository().count()).isEqualTo(1);

        // validación manual => confirmado y el tablero lo refleja
        long eventId = rows.eventId();
        assertThat(ctx.reviewService().pending(10)).extracting(i -> i.row().eventId()).contains(eventId);
        assertThat(ctx.reviewService().validate(eventId, "tester", "Visto en la app de Nequi"))
                .isEqualTo(EventStatus.VALIDADO_POR_FUENTE);
        DashboardSummary after = ctx.dashboardService().today();
        assertThat(after.confirmedTotal()).isEqualTo(50_000L);
        assertThat(after.confirmedCount()).isEqualTo(1);
        assertThat(after.unconfirmedTotal()).isZero();
        assertThat(ctx.reviewService().pending(10)).isEmpty();
        assertThat(android.stats().accepted()).isEqualTo(1);
        assertThat(android.stats().duplicates()).isEqualTo(1);
        assertThat(ctx.connectorRepository().loadCursor("android-principal")).isNotNull();
    }

    @Test
    void modoHmacFirmaValidaYFirmaInvalida() throws Exception {
        boot("hmac");
        byte[] secret = new String(android.revealSecret()).getBytes(StandardCharsets.UTF_8);

        assertThat(postHmac(json("md-2-1", NEQUI_TEXT), secret).statusCode()).isEqualTo(202);
        assertThat(ctx.transactionRepository().count()).isEqualTo(1);
        assertUnconfirmed50k(ctx.dashboardService().today());

        HttpResponse<String> bad = postHmac(json("md-2-2", "ANA te envió $99.000"),
                "otro-secreto-que-no-es-el-correcto-123456".getBytes(StandardCharsets.UTF_8));
        assertThat(bad.statusCode()).isEqualTo(401);
        assertThat(ctx.transactionRepository().count()).isEqualTo(1);
        assertUnconfirmed50k(ctx.dashboardService().today());
        assertThat(android.stats().rejected()).containsKey("bad_signature");

        // reenvío idéntico (misma firma nueva, mismo eventId) => duplicado
        HttpResponse<String> dup = postHmac(json("md-2-1", NEQUI_TEXT), secret);
        assertThat(dup.statusCode()).isEqualTo(200);
        assertThat(ctx.transactionRepository().count()).isEqualTo(1);
    }

    @Test
    void correoBancolombiaDeDominioFalsoVaARevision() throws Exception {
        boot("token");
        RawEvent fake = RawEvent.builder(EventSource.GMAIL, "gmail-principal")
                .externalEventId("gm-falso-1")
                .sourceTimestamp(ctx.clock().instant()).receivedAt(ctx.clock().instant())
                .subject("Alertas y Notificaciones")
                .sender("alertasynotificaciones@bancolombia-seguro.com")
                .rawText("Bancolombia le informa que recibió una transferencia por $50.000 de JUAN PEREZ en su cuenta *1234.")
                .build();
        var result = ctx.ingestionService().ingest(fake);
        assertThat(result.isPersisted()).isTrue();
        assertThat(result.status()).isEqualTo(EventStatus.PENDIENTE_REVISION);
        DashboardSummary s = ctx.dashboardService().today();
        assertThat(s.confirmedTotal()).isZero();
        assertThat(s.pendingReviewCount()).isEqualTo(1);
        ctx.voiceService().awaitIdle(Duration.ofSeconds(5));
        assertThat(speech.spoken()).noneMatch(t -> t.contains("Pago recibido"));
    }
}
