package com.vivanetpay.integration.android;

import com.vivanetpay.application.EventSink;
import com.vivanetpay.application.IngestionResult;
import com.vivanetpay.domain.RawEvent;
import com.vivanetpay.integration.ConnectorDependencies;
import com.vivanetpay.security.InMemorySecretStore;
import com.vivanetpay.support.TestApp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QA de seguridad y robustez del receptor Android (RF-022, RNF-003, IDD 8.1): Unicode raro, errores internos que no
 * deben filtrar trazas al cliente, inyección de cabeceras y tiempos máximos anti-slowloris.
 */
class QaWebhookHardeningTest {
    @TempDir
    Path dir;
    private TestApp app;
    private AndroidWebhookConnector connector;
    private final AtomicBoolean sinkFails = new AtomicBoolean();
    private final HttpClient http = HttpClient.newBuilder().proxy(HttpClient.Builder.NO_PROXY)
            .connectTimeout(Duration.ofSeconds(5)).build();

    @AfterEach
    void tearDown() {
        if (connector != null) {
            connector.stop();
        }
        if (app != null) {
            app.ctx.close();
        }
    }

    private void start() {
        app = TestApp.create(dir);
        EventSink real = app.ctx.ingestionService();
        EventSink sink = new EventSink() {
            @Override
            public IngestionResult ingest(RawEvent event) {
                if (sinkFails.get()) {
                    throw new IllegalStateException("secreto-interno jdbc:sqlite:/ruta/privada/vivanetpay.db");
                }
                return real.ingest(event);
            }

            @Override
            public CompletableFuture<IngestionResult> ingestAsync(RawEvent event) {
                return CompletableFuture.supplyAsync(() -> ingest(event));
            }
        };
        Map<String, String> settings = new HashMap<>();
        settings.put("port", "0");
        settings.put("auth", "hmac");
        connector = new AndroidWebhookConnector(new ConnectorDependencies("android-principal", sink,
                app.ctx.connectorRepository(), new InMemorySecretStore(), settings, dir, app.clock));
        connector.start();
        assertThat(connector.isRunning()).isTrue();
    }

    private HttpResponse<String> post(String body, String eventIdHeaderNonce) throws Exception {
        byte[] secret = new String(connector.revealSecret()).getBytes(StandardCharsets.UTF_8);
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        String ts = Long.toString(app.clock.instant().getEpochSecond());
        String nonce = eventIdHeaderNonce != null ? eventIdHeaderNonce : UUID.randomUUID().toString().replace("-", "");
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + connector.boundPort() + "/v1/events"))
                .header("Content-Type", "application/json; charset=utf-8")
                .header(WebhookSigner.HEADER_TIMESTAMP, ts)
                .header(WebhookSigner.HEADER_NONCE, nonce)
                .header(WebhookSigner.HEADER_SIGNATURE, WebhookSigner.signatureHeader(secret, ts, nonce, b))
                .POST(HttpRequest.BodyPublishers.ofByteArray(b)).build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private static String json(String eventId, String jsonEscapedText) {
        return "{\"schemaVersion\":1,\"eventId\":\"" + eventId + "\",\"title\":\"Nequi\",\"text\":\"" + jsonEscapedText
                + "\",\"capturedAt\":\"1795359600\"}";
    }

    @Test
    void unicodeRaroSeAceptaSeGuardaIntactoYNoRompeElReceptor() throws Exception {
        start();
        String[] texts = {
                "Ana 😀 te envió $5.000 🎉",              // emoji (pares sustitutos)
                "María José te envió $6.000 Ñandú",   // tildes y eñe
                "é́́ te envió $7.000",                           // marcas combinantes apiladas
                "Zalgo ̸̵ te envió $8.000 ​‍﻿",          // ancho cero / BOM
                "‮evil‬ te envió $9.000",                              // override bidireccional
                "＄５０．０００ te envió",           // dígitos y signos de ancho completo
                "١٢٣ te envió $١٢٣",                              // dígitos arábigo-índicos
                "𝒜𝒝 te envió $1.000",                      // plano astral matemático
        };
        int i = 0;
        for (String t : texts) {
            HttpResponse<String> r = post(json("uni-" + i++, t), null);
            assertThat(r.statusCode()).as(t).isIn(202, 400);
            assertThat(r.body()).doesNotContain("Exception").doesNotContain("\tat ");
        }
        // el texto con emoji queda guardado TAL CUAL (UTF-8 de ida y vuelta por JSON -> SQLite)
        var stored = app.ctx.eventRepository().findById(1L).orElseThrow();
        assertThat(stored.rawText()).contains("😀").contains("🎉");
        // sigue atendiendo tras todo lo anterior
        assertThat(post(json("uni-final", "Ana te envió $1.000"), null).statusCode()).isEqualTo(202);
    }

    @Test
    void sustitutoSueltoYCaracteresDeControlNoProducenError500() throws Exception {
        start();
        // surrogate suelto escapado (JSON válido pero UTF-16 inválido) y NUL: nunca 5xx ni excepción filtrada
        HttpResponse<String> lone = post(json("lone-1", "Ana te envió $1.000 \\ud800 fin"), null);
        assertThat(lone.statusCode()).isLessThan(500);
        HttpResponse<String> nul = post(json("nul-1", "Ana\\u0000te envió $1.000"), null);
        assertThat(nul.statusCode()).isEqualTo(400);
        assertThat(nul.body()).contains("invalid_schema").doesNotContain("Exception");
        HttpResponse<String> bell = post(json("bel-1", "Ana\\u0007 te envió $1.000"), null);
        assertThat(bell.statusCode()).isEqualTo(400);
    }

    @Test
    void erroresInternosNoFiltranTrazasNiRutasAlCliente() throws Exception {
        start();
        sinkFails.set(true);
        HttpResponse<String> r = post(json("boom-1", "Ana te envió $1.000"), null);
        assertThat(r.statusCode()).isEqualTo(500);
        assertThat(r.body()).isEqualTo("{\"error\":\"internal_error\"}");
        assertThat(r.body()).doesNotContain("secreto-interno", "sqlite", "Exception", "at com.");
        r.headers().map().forEach((k, v) -> assertThat(String.join(",", v)).doesNotContain("secreto-interno", "Exception"));
        sinkFails.set(false);
        assertThat(post(json("boom-2", "Ana te envió $1.000"), null).statusCode()).isEqualTo(202);
    }

    @Test
    void identificadoresConCaracteresDeControlOInyeccionDeCabecerasSeRechazan() throws Exception {
        start();
        assertThat(post(json("a\\r\\nX-Injected: 1", "Ana te envió $1.000"), null).statusCode()).isEqualTo(400);
        assertThat(post(json("a\\u0000b", "Ana te envió $1.000"), null).statusCode()).isEqualTo(400);
        // nonce con saltos de línea o demasiado corto/largo: rechazado por autenticación, nunca refleja el valor
        HttpResponse<String> shortNonce = post(json("n-1", "Ana te envió $1.000"), "abc");
        assertThat(shortNonce.statusCode()).isEqualTo(401);
        assertThat(shortNonce.body()).doesNotContain("abc");
        assertThat(app.ctx.transactionRepository().count()).isZero();
    }

    @Test
    void tiemposMaximosAntiSlowlorisConfigurados() {
        start();
        assertThat(System.getProperty("sun.net.httpserver.maxReqTime")).isNotNull();
        assertThat(Long.parseLong(System.getProperty("sun.net.httpserver.maxReqTime"))).isBetween(1L, 120L);
        assertThat(System.getProperty("sun.net.httpserver.maxRspTime")).isNotNull();
    }
}
