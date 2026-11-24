package com.vivanetpay.app;

import com.vivanetpay.domain.ConnectorStatus;
import com.vivanetpay.domain.ConnectorType;
import com.vivanetpay.integration.Connector;
import com.vivanetpay.voice.LoggingSpeechEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigPersistenceTest {
    @TempDir
    Path dir;

    @Test
    void updateConservaComentariosYClavesAjenasYReemplazaLaClave() throws Exception {
        Files.writeString(dir.resolve("config.properties"),
                "# mi configuración\nconnectors.enabled=android\nconnector.android.bindAddress=127.0.0.1\nvoice.volume=70\n",
                StandardCharsets.UTF_8);
        AppConfig c = AppConfig.load(dir);
        c.update(Map.of("connector.android.bindAddress", "127.0.0.1,192.168.1.20", "export.csv.separator", ";"));
        String text = Files.readString(dir.resolve("config.properties"), StandardCharsets.UTF_8);
        assertThat(text).contains("# mi configuración").contains("connectors.enabled=android").contains("voice.volume=70")
                .contains("connector.android.bindAddress=127.0.0.1,192.168.1.20").contains("export.csv.separator=;");
        assertThat(text.split("connector.android.bindAddress").length - 1).isEqualTo(1);
        AppConfig again = AppConfig.load(dir);
        assertThat(again.connectorSettings(ConnectorType.ANDROID_WEBHOOK)).containsEntry("bindAddress", "127.0.0.1,192.168.1.20");
        assertThat(c.get("connector.android.bindAddress", "")).isEqualTo("127.0.0.1,192.168.1.20");
        assertThat(Files.list(dir).filter(p -> p.getFileName().toString().endsWith(".tmp"))).isEmpty();
    }

    @Test
    void updateCreaElArchivoYPermiteEliminarClaves() throws Exception {
        AppConfig c = AppConfig.load(dir);
        c.set("policy.automaticValidation", "false");
        assertThat(AppConfig.load(dir).getBoolean("policy.automaticValidation", true)).isFalse();
        Map<String, String> rm = new HashMap<>();
        rm.put("policy.automaticValidation", null);
        c.update(rm);
        assertThat(AppConfig.load(dir).getBoolean("policy.automaticValidation", true)).isTrue();
        assertThat(Files.readString(dir.resolve("config.properties"))).doesNotContain("automaticValidation");
    }

    @Test
    void valoresConBarraInvertidaYAcentosSobrevivenAlViaje() {
        AppConfig c = AppConfig.load(dir);
        c.set("tls.path", "C:\\Users\\José\\ks.p12");
        assertThat(AppConfig.load(dir).get("tls.path", "")).isEqualTo("C:\\Users\\José\\ks.p12");
    }

    @Test
    void ipPublicaOComodinNoSePersisten() {
        AppConfig c = AppConfig.load(dir);
        for (String bad : new String[]{"8.8.8.8", "0.0.0.0", "::"}) {
            assertThatThrownBy(() -> LanReceiverSetup.persist(c, bad)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(Files.exists(dir.resolve("config.properties"))).isFalse();
    }

    @Test
    void persistGuardaLoopbackMasLaIpYSeRestaura() {
        AppConfig c = AppConfig.load(dir);
        String prev = LanReceiverSetup.persist(c, "192.168.1.20");
        assertThat(prev).isNull();
        assertThat(c.get(LanReceiverSetup.BIND_KEY, "")).isEqualTo("127.0.0.1,192.168.1.20");
        assertThat(LanReceiverSetup.configuredLanIp(c.connectorSettings(ConnectorType.ANDROID_WEBHOOK))).isEqualTo("192.168.1.20");
        assertThat(LanReceiverSetup.webhookUrl("192.168.1.20", 47390, false)).isEqualTo("http://192.168.1.20:47390/v1/events");
        assertThat(LanReceiverSetup.webhookUrl("192.168.1.20", 47390, true)).isEqualTo("https://192.168.1.20:47390/v1/events");
        LanReceiverSetup.disable(c);
        assertThat(AppConfig.load(dir).connectorSettings(ConnectorType.ANDROID_WEBHOOK)).doesNotContainKey("bindAddress");
    }

    private static final com.vivanetpay.integration.android.AndroidWebhookConnectorProvider REAL =
            new com.vivanetpay.integration.android.AndroidWebhookConnectorProvider();

    /** Contexto con el conector Android REAL (el SPI de pruebas registra uno falso con el mismo tipo). */
    private AppContext bootReal(Map<String, String> extra) {
        java.util.Map<String, String> m = new java.util.HashMap<>(Map.of("backup.onStart", "false", "connectors.enabled", "none"));
        m.putAll(extra);
        Bootstrap.Result r = Bootstrap.start(AppContext.builder(AppConfig.of(dir, m)).speechEngine(new LoggingSpeechEngine()));
        AppContext ctx = r.context();
        Connector c = REAL.create(new com.vivanetpay.integration.ConnectorDependencies(REAL.defaultConnectorId(),
                ctx.ingestionService(), ctx.connectorRepository(), ctx.secretStore(),
                ctx.config().connectorSettings(ConnectorType.ANDROID_WEBHOOK), dir, ctx.clock()));
        ctx.connectorRegistry().register(c);
        c.start();
        return ctx;
    }

    /** IP bindable de este equipo: una privada real si existe; si no, 127.0.0.2 (loopback, con applyUnchecked). */
    private static String anyLocalIpv4() {
        return "127.0.0.2";
    }

    @Test
    void aplicarReiniciaElConectorYEscuchaEnLaIpLocal() throws Exception {
        String ip = anyLocalIpv4();
        try (AppContext ctx = bootReal(Map.of("connector.android.port", "0"))) {
            assertThat(ctx.connectorRegistry().all()).hasSize(1);
            String url = LanReceiverSetup.applyUnchecked(ctx, ip, REAL);
            assertThat(url).startsWith("http://" + ip + ":").endsWith("/v1/events");
            Connector c = ctx.connectorRegistry().all().iterator().next();
            assertThat(c.state().status()).isEqualTo(ConnectorStatus.CONECTADO);
            assertThat(((com.vivanetpay.integration.android.AndroidWebhookConnector) c).endpoints()).hasSize(2);
            assertThat(ctx.config().get(LanReceiverSetup.BIND_KEY, "")).isEqualTo("127.0.0.1," + ip);
            assertThat(url).doesNotContain(":0/");
            LanReceiverSetup.revert(ctx, REAL);
            Connector back = ctx.connectorRegistry().all().iterator().next();
            assertThat(((com.vivanetpay.integration.android.AndroidWebhookConnector) back).endpoints()).hasSize(1);
        }
    }

    @Test
    void siElReceptorNoPuedeEscucharEnLaIpSeRestauraLaConfiguracion() throws Exception {
        String ip = anyLocalIpv4();
        // se ocupa el puerto SOLO en esa IP: el receptor arranca en 127.0.0.1 pero falla al añadir la IP
        try (java.net.ServerSocket busy = new java.net.ServerSocket()) {
            busy.bind(new java.net.InetSocketAddress(InetAddress.getByName(ip), 0));
            int port = busy.getLocalPort();
            try (AppContext ctx = bootReal(Map.of("connector.android.port", Integer.toString(port)))) {
                assertThat(ctx.connectorRegistry().all().iterator().next().state().status()).isEqualTo(ConnectorStatus.CONECTADO);
                assertThatThrownBy(() -> LanReceiverSetup.applyUnchecked(ctx, ip, REAL)).isInstanceOf(IllegalStateException.class);
                assertThat(ctx.config().connectorSettings(ConnectorType.ANDROID_WEBHOOK)).doesNotContainKey("bindAddress");
                assertThat(AppConfig.load(dir).connectorSettings(ConnectorType.ANDROID_WEBHOOK)).doesNotContainKey("bindAddress");
                assertThat(ctx.connectorRegistry().all().iterator().next().state().status()).isEqualTo(ConnectorStatus.CONECTADO);
            }
        }
    }

    @Test
    void aplicarRechazaIpPublicaSinTocarLaConfiguracion() {
        try (AppContext ctx = bootReal(Map.of("connector.android.port", "0"))) {
            assertThatThrownBy(() -> LanReceiverSetup.apply(ctx, "8.8.8.8")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> LanReceiverSetup.apply(ctx, "0.0.0.0")).isInstanceOf(IllegalArgumentException.class);
            assertThat(ctx.config().connectorSettings(ConnectorType.ANDROID_WEBHOOK)).doesNotContainKey("bindAddress");
        }
    }
}
