package com.vivanetpay.integration.android;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.vivanetpay.application.EventSink;
import com.vivanetpay.application.IngestionResult;
import com.vivanetpay.domain.ConnectorStatus;
import com.vivanetpay.domain.EventSource;
import com.vivanetpay.domain.RawEvent;
import com.vivanetpay.integration.ConnectorDependencies;
import com.vivanetpay.integration.ConnectorProvider;
import com.vivanetpay.security.InMemorySecretStore;
import com.vivanetpay.support.TestApp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/** Pruebas de integración del receptor (CP-007, CP-008, CP-009) con servidor real en puerto efímero. */
class AndroidWebhookConnectorTest {
    @TempDir
    Path dir;

    private TestApp app;
    private AndroidWebhookConnector connector;
    private InMemorySecretStore secretStore;
    private final List<RawEvent> ingested = new CopyOnWriteArrayList<>();
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

    private AndroidWebhookConnector start(Map<String, String> extra) {
        app = TestApp.create(dir);
        secretStore = new InMemorySecretStore();
        Map<String, String> settings = new HashMap<>();
        settings.put("port", "0");
        settings.put("auth", "hmac"); // el valor por defecto es token; estas pruebas ejercitan HMAC salvo que extra lo cambie
        settings.putAll(extra);
        EventSink real = app.ctx.ingestionService();
        EventSink capturing = new EventSink() {
            @Override
            public IngestionResult ingest(RawEvent event) {
                ingested.add(event);
                return real.ingest(event);
            }

            @Override
            public CompletableFuture<IngestionResult> ingestAsync(RawEvent event) {
                ingested.add(event);
                return real.ingestAsync(event);
            }
        };
        connector = new AndroidWebhookConnector(new ConnectorDependencies("android-principal", capturing,
                app.ctx.connectorRepository(), secretStore, settings, dir, app.clock));
        connector.start();
        assertThat(connector.isRunning()).as("receptor iniciado: " + connector.state().detail()).isTrue();
        return connector;
    }

    private URI eventsUri() {
        return connector.endpoints().get(0);
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + connector.boundPort() + path);
    }

    private byte[] secret() {
        char[] s = connector.revealSecret();
        return new String(s).getBytes(StandardCharsets.UTF_8);
    }

    static String payload(String eventId, String text) {
        return """
                {"schemaVersion":1,"eventId":"%s","source":"ANDROID_NEQUI","app":"com.nequi.MobileApp",\
                "title":"Nequi","text":"%s","capturedAt":"1795359600","device":"Moto G"}""".formatted(eventId, text);
    }

    private HttpResponse<String> postHmac(String body, byte[] secret, long ts, String nonce) throws Exception {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        String t = Long.toString(ts);
        HttpRequest req = HttpRequest.newBuilder(eventsUri())
                .header("Content-Type", "application/json; charset=utf-8")
                .header(WebhookSigner.HEADER_TIMESTAMP, t)
                .header(WebhookSigner.HEADER_NONCE, nonce)
                .header(WebhookSigner.HEADER_SIGNATURE, WebhookSigner.signatureHeader(secret, t, nonce, b))
                .POST(HttpRequest.BodyPublishers.ofByteArray(b)).build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postHmac(String body) throws Exception {
        return postHmac(body, secret(), now(), nonce());
    }

    private HttpResponse<String> postToken(String body, String token) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(eventsUri())
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private long now() {
        return app.clock.instant().getEpochSecond();
    }

    private static String nonce() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private long transactions() {
        return app.ctx.transactionRepository().count();
    }

    // ------------------------------------------------------------------ CP-007

    @Test
    void eventoValidoHmacSeAceptaYSeMapeaARawEvent() throws Exception {
        start(Map.of());
        HttpResponse<String> r = postHmac(payload("md-1001", "Ana te envió $15.000"));
        assertThat(r.statusCode()).isEqualTo(202);
        assertThat(r.body()).contains("\"accepted\"").contains("md-1001");
        assertThat(transactions()).isEqualTo(1);

        RawEvent e = ingested.get(0);
        assertThat(e.source()).isEqualTo(EventSource.ANDROID_NEQUI);
        assertThat(e.connectorId()).isEqualTo("android-principal");
        assertThat(e.externalEventId()).isEqualTo("md-1001");
        assertThat(e.sourceTimestamp().getEpochSecond()).isEqualTo(1795359600L);
        assertThat(e.receivedAt()).isEqualTo(TestApp.T0);
        assertThat(e.rawText()).isEqualTo("Nequi\nAna te envió $15.000");
        assertThat(e.subject()).isEqualTo("Nequi");
        assertThat(e.sender()).isEqualTo("com.nequi.MobileApp");
        assertThat(e.historical()).isFalse();
        assertThat(e.headers()).containsEntry("android.device", "Moto G").containsEntry("webhook.auth", "hmac");
        assertThat(String.join(",", e.headers().values())).doesNotContain(new String(secret(), StandardCharsets.UTF_8));
        assertThat(connector.state().status()).isEqualTo(ConnectorStatus.CONECTADO);
        assertThat(connector.state().lastSuccessAt()).isNotNull();
        assertThat(connector.stats().accepted()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ CP-008

    @Test
    void firmaInvalidaSeRechazaSinCrearPago() throws Exception {
        start(Map.of());
        byte[] wrong = "otro-secreto-que-no-es-el-correcto-123456".getBytes(StandardCharsets.UTF_8);
        HttpResponse<String> r = postHmac(payload("md-1", "Ana te envió $15.000"), wrong, now(), nonce());
        assertThat(r.statusCode()).isEqualTo(401);
        assertThat(r.body()).contains("bad_signature");

        // cuerpo alterado tras firmar
        String body = payload("md-2", "Ana te envió $15.000");
        String t = Long.toString(now());
        String n = nonce();
        String sig = WebhookSigner.signatureHeader(secret(), t, n, body.getBytes(StandardCharsets.UTF_8));
        HttpResponse<String> r2 = http.send(HttpRequest.newBuilder(eventsUri())
                .header("Content-Type", "application/json")
                .header(WebhookSigner.HEADER_TIMESTAMP, t).header(WebhookSigner.HEADER_NONCE, n)
                .header(WebhookSigner.HEADER_SIGNATURE, sig)
                .POST(HttpRequest.BodyPublishers.ofString(body.replace("15.000", "950.000"))).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(r2.statusCode()).isEqualTo(401);

        // sin cabeceras
        HttpResponse<String> r3 = postToken(payload("md-3", "x"), "nada");
        assertThat(r3.statusCode()).isEqualTo(401);
        assertThat(r3.headers().firstValue("WWW-Authenticate")).isPresent();

        assertThat(transactions()).isZero();
        assertThat(ingested).isEmpty();
        assertThat(app.ctx.eventRepository().count()).isZero();
        assertThat(connector.stats().totalRejected()).isEqualTo(3);
    }

    @Test
    void modoTokenAceptaTokenValidoYRechazaInvalido() throws Exception {
        start(Map.of("auth", "token"));
        String token = new String(connector.revealSecret());
        assertThat(token).hasSizeGreaterThanOrEqualTo(43); // 32 bytes en Base64URL

        assertThat(postToken(payload("tk-1", "Ana te envió $20.000"), token + "x").statusCode()).isEqualTo(401);
        assertThat(postToken(payload("tk-1", "Ana te envió $20.000"), "").statusCode()).isEqualTo(401);
        assertThat(transactions()).isZero();

        HttpResponse<String> ok = postToken(payload("tk-1", "Ana te envió $20.000"), token);
        assertThat(ok.statusCode()).isEqualTo(202);
        assertThat(ingested.get(0).headers()).containsEntry("webhook.auth", "token");
        // reenvío del mismo evento (reintento de MacroDroid): 200 duplicado, sin segundo movimiento (CP-009)
        HttpResponse<String> again = postToken(payload("tk-1", "Ana te envió $20.000"), token);
        assertThat(again.statusCode()).isEqualTo(200);
        assertThat(again.body()).contains("duplicate");
        assertThat(transactions()).isEqualTo(1);
    }

    @Test
    void timestampVencidoOFuturoSeRechaza() throws Exception {
        start(Map.of());
        String body = payload("md-old", "Ana te envió $15.000");
        assertThat(postHmac(body, secret(), now() - 301, nonce()).statusCode()).isEqualTo(401);
        assertThat(postHmac(body, secret(), now() + 301, nonce()).statusCode()).isEqualTo(401);
        assertThat(postHmac(body, secret(), now() - 299, nonce()).statusCode()).isEqualTo(202);
        assertThat(connector.stats().rejected()).containsEntry("timestamp_out_of_window", 2L);
    }

    // ------------------------------------------------------------------ CP-009

    @Test
    void replayDeNonceDevuelve409YNoDuplica() throws Exception {
        start(Map.of());
        String body = payload("md-r", "Ana te envió $15.000");
        long ts = now();
        String n = nonce();
        assertThat(postHmac(body, secret(), ts, n).statusCode()).isEqualTo(202);
        HttpResponse<String> replay = postHmac(body, secret(), ts, n);
        assertThat(replay.statusCode()).isEqualTo(409);
        assertThat(replay.body()).contains("replay");
        assertThat(transactions()).isEqualTo(1);
        assertThat(ingested).hasSize(1);
    }

    @Test
    void reenvioMismoEventIdConNuevoNonceEsIdempotente() throws Exception {
        start(Map.of());
        String body = payload("md-same", "Ana te envió $15.000");
        assertThat(postHmac(body).statusCode()).isEqualTo(202);
        HttpResponse<String> r2 = postHmac(body);
        HttpResponse<String> r3 = postHmac(body);
        assertThat(r2.statusCode()).isEqualTo(200);
        assertThat(r3.statusCode()).isEqualTo(200);
        assertThat(r2.body()).contains("duplicate");
        assertThat(transactions()).isEqualTo(1);
        assertThat(connector.stats().duplicates()).isEqualTo(2);
    }

    // ------------------------------------------------------------------ límites y validación

    @Test
    void cuerpoGiganteSeRechazaConTopeReal() throws Exception {
        start(Map.of());
        String big = payload("md-big", "Ana te envió $15.000 " + "x".repeat(200_000));
        HttpResponse<String> r = postHmac(big);
        assertThat(r.statusCode()).isEqualTo(413);

        // sin Content-Length (chunked): el servidor corta al superar el tope
        byte[] chunked = payload("md-big2", "y".repeat(20_000)).getBytes(StandardCharsets.UTF_8);
        String t = Long.toString(now());
        String n = nonce();
        HttpResponse<String> r2 = http.send(HttpRequest.newBuilder(eventsUri())
                .header("Content-Type", "application/json")
                .header(WebhookSigner.HEADER_TIMESTAMP, t).header(WebhookSigner.HEADER_NONCE, n)
                .header(WebhookSigner.HEADER_SIGNATURE, WebhookSigner.signatureHeader(secret(), t, n, chunked))
                .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(chunked))).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(r2.statusCode()).isEqualTo(413);
        assertThat(transactions()).isZero();
    }

    @Test
    void readCappedNoLeeMasAllaDelTope() throws Exception {
        byte[] data = new byte[100];
        assertThat(AndroidWebhookConnector.readCapped(new ByteArrayInputStream(data), 100)).hasSize(100);
        assertThat(AndroidWebhookConnector.readCapped(new ByteArrayInputStream(new byte[101]), 100)).isNull();
    }

    @Test
    void jsonMalformadoOEsquemaInvalidoDevuelve400() throws Exception {
        start(Map.of());
        assertThat(postHmac("{\"schemaVersion\":1,").statusCode()).isEqualTo(400);
        assertThat(postHmac("[1,2]").statusCode()).isEqualTo(400);
        HttpResponse<String> unknown = postHmac(payload("md-u", "Ana te envió $1.000").replace("}", ",\"amount\":5}"));
        assertThat(unknown.statusCode()).isEqualTo(400);
        assertThat(unknown.body()).contains("unknown_field");
        assertThat(postHmac(payload("md-v", "x").replace("\"schemaVersion\":1", "\"schemaVersion\":2")).body())
                .contains("unsupported_schema_version");
        assertThat(postHmac(payload("", "Ana te envió $1.000")).statusCode()).isEqualTo(400);
        assertThat(postHmac(payload("md-t", "   ")).statusCode()).isEqualTo(400);
        assertThat(postHmac(payload("md-l", "z".repeat(4097))).statusCode()).isEqualTo(400);
        assertThat(postHmac(payload("md-c", "Ana te envió $1.000").replace("1795359600", "hace un rato")).statusCode())
                .isEqualTo(400);
        assertThat(postHmac(payload("md-d", "a").replace("\"title\":\"Nequi\"", "\"title\":\"Nequi\",\"title\":\"X\""))
                .statusCode()).isEqualTo(400);
        assertThat(postHmac(payload("md-s", "a").replace("ANDROID_NEQUI", "GMAIL")).statusCode()).isEqualTo(400);
        assertThat(transactions()).isZero();
        assertThat(ingested).isEmpty();
    }

    @Test
    void metodoYTipoDeContenidoIncorrectos() throws Exception {
        start(Map.of());
        HttpResponse<String> get = http.send(HttpRequest.newBuilder(eventsUri()).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(get.statusCode()).isEqualTo(405);
        assertThat(get.headers().firstValue("Allow")).contains("POST");
        HttpResponse<String> put = http.send(HttpRequest.newBuilder(eventsUri())
                .PUT(HttpRequest.BodyPublishers.ofString("{}")).header("Content-Type", "application/json").build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(put.statusCode()).isEqualTo(405);
        HttpResponse<String> form = http.send(HttpRequest.newBuilder(eventsUri())
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("a=b")).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(form.statusCode()).isEqualTo(415);
        HttpResponse<String> notFound = http.send(HttpRequest.newBuilder(uri("/admin")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(notFound.statusCode()).isEqualTo(404);
        assertThat(transactions()).isZero();
    }

    @Test
    void rateLimitPorIpDevuelve429() throws Exception {
        start(Map.of("rateLimit.burst", "3", "rateLimit.perMinute", "1"));
        List<Integer> codes = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            codes.add(http.send(HttpRequest.newBuilder(uri("/health")).GET().build(),
                    HttpResponse.BodyHandlers.ofString()).statusCode());
        }
        assertThat(codes).containsExactly(200, 200, 200, 429, 429);
        HttpResponse<String> r = postHmac(payload("md-rl", "Ana te envió $15.000"));
        assertThat(r.statusCode()).isEqualTo(429);
        assertThat(r.headers().firstValue("Retry-After")).isPresent();
        assertThat(transactions()).isZero();
    }

    @Test
    void healthNoExponeDatosSensibles() throws Exception {
        start(Map.of());
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(uri("/health")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.body()).isEqualTo("{\"status\":\"ok\",\"schemaVersion\":1}");
        assertThat(r.headers().firstValue("Cache-Control")).contains("no-store");
    }

    @Test
    void enPausaRechazaSinCrearPago() throws Exception {
        start(Map.of());
        connector.pause();
        HttpResponse<String> r = postHmac(payload("md-p", "Ana te envió $15.000"));
        assertThat(r.statusCode()).isEqualTo(503);
        assertThat(transactions()).isZero();
        connector.resume();
        assertThat(postHmac(payload("md-p", "Ana te envió $15.000")).statusCode()).isEqualTo(202);
    }

    // ------------------------------------------------------------------ secreto

    @Test
    void rotacionDeSecretoConPeriodoDeGracia() throws Exception {
        start(Map.of("rotationGraceMinutes", "10"));
        byte[] old = secret();
        assertThat(secretStore.contains("android.android-principal.webhook_secret")).isTrue();
        char[] fresh = connector.rotateSecret();
        byte[] neu = new String(fresh).getBytes(StandardCharsets.UTF_8);
        assertThat(neu).isNotEqualTo(old);
        assertThat(connector.previousSecretValidUntil()).isEqualTo(TestApp.T0.plus(Duration.ofMinutes(10)));

        assertThat(postHmac(payload("rot-1", "Ana te envió $1.000"), old, now(), nonce()).statusCode()).isEqualTo(202);
        assertThat(postHmac(payload("rot-2", "Ana te envió $2.000"), neu, now(), nonce()).statusCode()).isEqualTo(202);

        app.clock.advance(Duration.ofMinutes(11));
        assertThat(postHmac(payload("rot-3", "Ana te envió $3.000"), old, now(), nonce()).statusCode()).isEqualTo(401);
        assertThat(postHmac(payload("rot-4", "Ana te envió $4.000"), neu, now(), nonce()).statusCode()).isEqualTo(202);
        assertThat(connector.previousSecretValidUntil()).isNull();
        assertThat(secretStore.contains("android.android-principal.webhook_secret_previous")).isFalse();

        // revocación inmediata: sin gracia
        char[] revoked = connector.revokeSecret();
        assertThat(postHmac(payload("rot-5", "Ana te envió $5.000"), neu, now(), nonce()).statusCode()).isEqualTo(401);
        assertThat(postHmac(payload("rot-6", "Ana te envió $6.000"),
                new String(revoked).getBytes(StandardCharsets.UTF_8), now(), nonce()).statusCode()).isEqualTo(202);
    }

    @Test
    void secretoPersisteEntreReinicios() {
        start(Map.of());
        String first = new String(connector.revealSecret());
        connector.stop();
        connector.start();
        assertThat(new String(connector.revealSecret())).isEqualTo(first);
    }

    @Test
    void logsNoContienenSecretosFirmasNiCuerpo() throws Exception {
        Logger pkg = (Logger) LoggerFactory.getLogger("com.vivanetpay");
        Level prev = pkg.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        pkg.addAppender(appender);
        pkg.setLevel(Level.DEBUG);
        try {
            start(Map.of());
            String secretStr = new String(connector.revealSecret());
            String body = payload("md-log", "Carlos Pérez te envió $77.000");
            long ts = now();
            String n = nonce();
            String sig = WebhookSigner.signatureHeader(secret(), Long.toString(ts), n, body.getBytes(StandardCharsets.UTF_8));
            postHmac(body, secret(), ts, n);
            postHmac(body, secret(), ts, n); // replay
            postHmac(body, "malo-malo-malo-malo-malo-malo-malo-malo".getBytes(StandardCharsets.UTF_8), ts, nonce());
            postHmac(payload("md-log2", "Carlos Pérez te envió $77.000").replace("}", ",\"zz\":1}"));
            connector.rotateSecret();
            connector.sendTestEvent();
            String tokenSecret;
            connector.stop();
            connector = null;
            app.ctx.close();
            app = null;
            // modo token
            start(Map.of("auth", "token"));
            tokenSecret = new String(connector.revealSecret());
            postToken(payload("tk-log", "Carlos Pérez te envió $77.000"), tokenSecret + "mal");
            connector.sendTestEvent();

            StringBuilder all = new StringBuilder();
            StringBuilder android = new StringBuilder();
            for (ILoggingEvent e : appender.list) {
                StringBuilder line = new StringBuilder(e.getFormattedMessage()).append('\n');
                if (e.getThrowableProxy() != null) {
                    line.append(ThrowableProxyUtil.asString(e.getThrowableProxy())).append('\n');
                }
                all.append(line);
                if (e.getLoggerName().startsWith("com.vivanetpay.integration.android")
                        || e.getLoggerName().startsWith("com.vivanetpay.security")) {
                    android.append(line);
                }
            }
            String logs = all.toString();
            assertThat(android.toString()).contains("Webhook Android rechazado"); // hubo auditoría
            // Ningún log (de ningún componente) contiene secretos, firmas ni nonces.
            assertThat(logs).doesNotContain(secretStr).doesNotContain(tokenSecret)
                    .doesNotContain(sig).doesNotContain(sig.substring(3)).doesNotContain(n);
            // El receptor no registra el cuerpo (texto de la notificación).
            assertThat(android.toString()).doesNotContain("Carlos").doesNotContain("77.000");
        } finally {
            pkg.detachAppender(appender);
            pkg.setLevel(prev);
        }
    }

    // ------------------------------------------------------------------ prueba de recepción

    @Test
    void sendTestEventNoCreaPagos() {
        start(Map.of());
        WebhookTestResult r = connector.sendTestEvent();
        assertThat(r.ok()).as(r.message()).isTrue();
        assertThat(r.httpStatus()).isEqualTo(200);
        assertThat(transactions()).isZero();
        assertThat(ingested).isEmpty();
        assertThat(app.ctx.eventRepository().count()).isZero();
        assertThat(connector.stats().tests()).isEqualTo(1);
    }

    @Test
    void sendTestEventEnModoToken() {
        start(Map.of("auth", "token"));
        assertThat(connector.sendTestEvent().ok()).isTrue();
        assertThat(transactions()).isZero();
    }

    // ------------------------------------------------------------------ exposición de red

    @Test
    void direccionPublicaSinTlsNoArranca() {
        app = TestApp.create(dir);
        connector = new AndroidWebhookConnector(new ConnectorDependencies("android-principal", app.ctx.ingestionService(),
                app.ctx.connectorRepository(), new InMemorySecretStore(), Map.of("bindAddress", "8.8.8.8", "port", "0"),
                dir, app.clock));
        connector.start();
        assertThat(connector.isRunning()).isFalse();
        assertThat(connector.state().status()).isEqualTo(ConnectorStatus.ERROR);
        assertThat(connector.state().detail()).contains("HTTPS");
    }

    @Test
    void comodinBloqueadoPorDefectoYNombresNoPermitidos() {
        app = TestApp.create(dir);
        for (String bind : List.of("0.0.0.0", "localhost")) {
            connector = new AndroidWebhookConnector(new ConnectorDependencies("android-principal", app.ctx.ingestionService(),
                    app.ctx.connectorRepository(), new InMemorySecretStore(), Map.of("bindAddress", bind, "port", "0"),
                    dir, app.clock));
            connector.start();
            assertThat(connector.isRunning()).as(bind).isFalse();
            assertThat(connector.state().status()).isEqualTo(ConnectorStatus.ERROR);
        }
    }

    // ------------------------------------------------------------------ formulario (comillas en notificaciones)

    private static String enc(String v) {
        return java.net.URLEncoder.encode(v, StandardCharsets.UTF_8);
    }

    private static String form(String eventId, String title, String text) {
        return "schemaVersion=1&eventId=" + enc(eventId) + "&source=ANDROID_NEQUI&app=Nequi&title=" + enc(title)
                + "&text=" + enc(text) + "&capturedAt=1795359600&device=" + enc("Celular caja");
    }

    private HttpResponse<String> postFormToken(String body, String token) throws Exception {
        return http.send(HttpRequest.newBuilder(eventsUri())
                .header("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void formularioConComillasYSaltosDeLineaSeAceptaConTokenYMismaAutenticacion() throws Exception {
        start(Map.of("auth", "token"));
        String text = "JUAN \"EL PAISA\" PÉREZ te envió $50.000\nMensaje: \"hola\" & más 100%";
        HttpResponse<String> r = postFormToken(form("md-form-1", "Nequi", text), new String(secret(), StandardCharsets.UTF_8));
        assertThat(r.statusCode()).isEqualTo(202);
        assertThat(ingested).singleElement().satisfies(e -> {
            assertThat(e.rawText()).isEqualTo("Nequi\n" + text);
            assertThat(e.externalEventId()).isEqualTo("md-form-1");
        });
        // mismo eventId => duplicado, igual que con JSON
        assertThat(postFormToken(form("md-form-1", "Nequi", text), new String(secret(), StandardCharsets.UTF_8))
                .body()).contains("duplicate");
        assertThat(transactions()).isEqualTo(1);
        // sin token / token malo => 401 y sin pago
        assertThat(postFormToken(form("md-form-2", "Nequi", text), "malo").statusCode()).isEqualTo(401);
        assertThat(transactions()).isEqualTo(1);
    }

    @Test
    void formularioFirmadoConHmac() throws Exception {
        start(Map.of());
        String body = form("md-form-h", "Nequi", "Ana \"A\" te envió $15.000");
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        String t = Long.toString(now());
        String n = nonce();
        HttpRequest.Builder rb = HttpRequest.newBuilder(eventsUri())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header(WebhookSigner.HEADER_TIMESTAMP, t).header(WebhookSigner.HEADER_NONCE, n)
                .header(WebhookSigner.HEADER_SIGNATURE, WebhookSigner.signatureHeader(secret(), t, n, b));
        assertThat(http.send(rb.POST(HttpRequest.BodyPublishers.ofByteArray(b)).build(),
                HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(202);
        assertThat(transactions()).isEqualTo(1);
    }

    @Test
    void formularioEstrictoRechazaCamposDesconocidosRepetidosYCodificacionInvalida() throws Exception {
        start(Map.of("auth", "token"));
        String tok = new String(secret(), StandardCharsets.UTF_8);
        String ok = form("md-form-3", "Nequi", "Ana te envió $15.000");
        assertThat(postFormToken(ok + "&extra=1", tok).statusCode()).isEqualTo(400);
        assertThat(postFormToken(ok + "&eventId=otro", tok).statusCode()).isEqualTo(400);
        assertThat(postFormToken(ok + "&title=%ZZ", tok).statusCode()).isEqualTo(400);
        assertThat(postFormToken(ok.replace("schemaVersion=1", "schemaVersion=2"), tok).statusCode()).isEqualTo(400);
        assertThat(postFormToken("", tok).statusCode()).isEqualTo(400);
        // mismo tope de tamaño
        assertThat(postFormToken(form("md-form-4", "Nequi", "x".repeat(20_000)), tok).statusCode()).isEqualTo(413);
        assertThat(transactions()).isZero();
        // la prueba también se admite por formulario y no crea pagos
        assertThat(postFormToken(ok + "&test=true", tok).body()).contains("test_ok");
        assertThat(transactions()).isZero();
    }

    @Test
    void porDefectoSoloLoopbackYSinAdvertencias() {
        start(Map.of());
        assertThat(connector.config().bindAddresses()).containsExactly("127.0.0.1");
        assertThat(connector.endpoints()).singleElement()
                .satisfies(u -> assertThat(u.toString()).startsWith("http://127.0.0.1:").endsWith("/v1/events"));
        assertThat(connector.securityWarnings()).isEmpty();
        assertThat(connector.tlsEnabled()).isFalse();
    }

    @Test
    void clasificacionDeRedesPrivadas() throws Exception {
        for (String ip : List.of("10.0.0.5", "172.16.3.4", "192.168.1.20", "100.100.1.1", "169.254.1.1", "fd12::1", "127.0.0.1")) {
            assertThat(AndroidWebhookConnector.isPrivate(java.net.InetAddress.getByName(ip))).as(ip).isTrue();
        }
        for (String ip : List.of("8.8.8.8", "181.50.1.1", "2001:4860::8888")) {
            assertThat(AndroidWebhookConnector.isPrivate(java.net.InetAddress.getByName(ip))).as(ip).isFalse();
        }
    }

    @Test
    void proveedorRegistradoPorSpi() {
        assertThat(ServiceLoader.load(ConnectorProvider.class).stream().map(ServiceLoader.Provider::type))
                .contains(AndroidWebhookConnectorProvider.class);
        assertThat(new AndroidWebhookConnectorProvider().defaultConnectorId()).isEqualTo("android-principal");
    }
}
