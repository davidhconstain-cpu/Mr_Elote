package com.vivanetpay.app;

import com.vivanetpay.application.AppEvent;
import com.vivanetpay.domain.ConnectorStatus;
import com.vivanetpay.domain.ConnectorType;
import com.vivanetpay.licensing.LicenseStatus;
import com.vivanetpay.support.FakeNequiParser;
import com.vivanetpay.support.MutableClock;
import com.vivanetpay.voice.LoggingSpeechEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class BootstrapTest {
    @TempDir
    Path dir;

    @Test
    void arrancaRegistraConectoresYAvanzaCursorTrasPersistir() throws Exception {
        AppConfig config = AppConfig.of(dir, Map.of("connectors.enabled", "android,gmail"));
        LoggingSpeechEngine speech = new LoggingSpeechEngine();
        MutableClock clock = new MutableClock(Instant.parse("2026-11-22T15:00:00Z"));
        Bootstrap.Result r = Bootstrap.start(AppContext.builder(config).clock(clock).speechEngine(speech)
                .parser(new FakeNequiParser()));
        AppContext ctx = r.context();
        try {
            assertThat(r.databaseHealthy()).isTrue();
            assertThat(r.license().status()).isEqualTo(LicenseStatus.PRUEBA_ACTIVA);
            assertThat(r.startedConnectors()).containsExactly("android-principal");
            assertThat(r.missingProviders()).containsExactly(ConnectorType.GMAIL);
            try (var files = Files.list(config.backupsDir())) {
                assertThat(files.count()).as("respaldo de inicio").isEqualTo(1);
            }
            assertThat(ctx.connectorRepository().find("android-principal").orElseThrow().status())
                    .isEqualTo(ConnectorStatus.CONECTADO);

            var connector = (FakeConnectorProvider.FakeConnector) ctx.connectorRegistry().get("android-principal").orElseThrow();
            connector.receive("evt-1", "Ana te envió $15.000");
            assertThat(ctx.connectorRepository().loadCursor("android-principal")).contains("evt-1");
            assertThat(ctx.transactionRepository().count()).isEqualTo(1);

            List<AppEvent> events = new CopyOnWriteArrayList<>();
            ctx.eventBus().subscribe(AppEvent.ConnectorStatusChanged.class, events::add);
            connector.fail("Sin red");
            assertThat(events).singleElement().satisfies(e ->
                    assertThat(((AppEvent.ConnectorStatusChanged) e).current().status()).isEqualTo(ConnectorStatus.DESCONECTADO));
            ctx.voiceService().awaitIdle(Duration.ofSeconds(5));
            assertThat(speech.spoken()).contains("Atención: la conexión de Android está desconectada.");

            connector.pause();
            assertThat(ctx.connectorRepository().find("android-principal").orElseThrow().status())
                    .isEqualTo(ConnectorStatus.PAUSADO);
        } finally {
            ctx.close();
        }
    }

    @Test
    void directorioDeDatosPorDefecto() {
        String prev = System.getProperty(AppConfig.DATA_DIR_PROPERTY);
        try {
            System.setProperty(AppConfig.DATA_DIR_PROPERTY, dir.toString());
            assertThat(AppConfig.defaultDataDir()).isEqualTo(dir);
            System.clearProperty(AppConfig.DATA_DIR_PROPERTY);
            assertThat(AppConfig.defaultDataDir().toString()).satisfiesAnyOf(
                    p -> assertThat(p).endsWith(AppConfig.APP_FOLDER_UNIX),
                    p -> assertThat(p).endsWith(AppConfig.APP_FOLDER_WINDOWS));
        } finally {
            if (prev != null) {
                System.setProperty(AppConfig.DATA_DIR_PROPERTY, prev);
            }
        }
    }

    @Test
    void configuracionPorDefectoEsConservadora() {
        AppConfig c = AppConfig.of(dir, Map.of());
        assertThat(c.policyConfig().automaticValidationEnabled()).isFalse();
        assertThat(c.policyConfig().trustedSources()).isEmpty();
        assertThat(c.voiceSettings().includeSender()).isFalse();
        assertThat(c.licenseEnforce()).isFalse();
        assertThat(c.enabledConnectors()).isEmpty();
    }
}
