package com.vivanetpay.application;

import com.vivanetpay.domain.AuditEntry;
import com.vivanetpay.domain.ConnectorState;
import com.vivanetpay.domain.ConnectorStatus;
import com.vivanetpay.domain.ConnectorType;
import com.vivanetpay.support.TestApp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** RF-025: alerta cuando un conector pierde conexión o su autorización vence; RF-017: persistencia y auditoría. */
class ConnectorMonitorTest {
    @TempDir
    Path dir;
    TestApp app;
    List<AppEvent.ConnectorStatusChanged> published = new ArrayList<>();

    @BeforeEach
    void setUp() {
        app = TestApp.create(dir, Map.of());
        app.ctx.eventBus().subscribe(AppEvent.ConnectorStatusChanged.class, published::add);
    }

    @AfterEach
    void tearDown() {
        app.ctx.close();
    }

    private ConnectorMonitor monitor(boolean voiceAlerts) {
        return new ConnectorMonitor(app.ctx.connectorRepository(), app.ctx.auditRepository(), app.ctx.eventBus(),
                app.ctx.voiceService(), voiceAlerts);
    }

    private ConnectorState state(ConnectorType type, String id, ConnectorStatus st, String detail) {
        return new ConnectorState(id, type, st, detail, null, app.clock.instant());
    }

    @Test
    void perdidaDeConexionPublicaEventoPersisteAuditaYAlertaPorVoz() throws Exception {
        ConnectorState ok = state(ConnectorType.ANDROID_WEBHOOK, "android-principal", ConnectorStatus.CONECTADO, "Escuchando");
        ConnectorState down = ok.withStatus(ConnectorStatus.DESCONECTADO, "Sin conexión con el celular", app.clock.instant());
        ConnectorMonitor m = monitor(true);
        m.onStateChanged(null, ok);
        m.onStateChanged(ok, down);

        assertThat(published).hasSize(2);
        assertThat(published.get(1).current().status()).isEqualTo(ConnectorStatus.DESCONECTADO);
        assertThat(app.ctx.connectorRepository().find("android-principal")).get()
                .extracting(ConnectorState::status).isEqualTo(ConnectorStatus.DESCONECTADO);
        assertThat(app.ctx.auditRepository().findByEntity(AuditEntry.ENTITY_CONNECTOR, "android-principal"))
                .anySatisfy(a -> {
                    assertThat(a.beforeValue()).isEqualTo("CONECTADO");
                    assertThat(a.afterValue()).isEqualTo("DESCONECTADO");
                });
        app.ctx.voiceService().awaitIdle(Duration.ofSeconds(5));
        assertThat(app.speech.spoken()).anySatisfy(s -> assertThat(s)
                .contains("Atención: la conexión de Android").contains("desconectada"));
    }

    @Test
    void autorizacionVencidaDeGmailAlertaYAvisaEnEvento() throws Exception {
        ConnectorState ok = state(ConnectorType.GMAIL, "gmail-principal", ConnectorStatus.CONECTADO, "Sincronizado");
        ConnectorState expired = ok.withStatus(ConnectorStatus.REQUIERE_AUTORIZACION,
                "La autorización de Gmail venció; pulse Autorizar", app.clock.instant());
        ConnectorMonitor m = monitor(true);
        m.onStateChanged(null, ok);
        m.onStateChanged(ok, expired);

        assertThat(published.get(1).current().status().needsAttention()).isTrue();
        assertThat(published.get(1).previous().status()).isEqualTo(ConnectorStatus.CONECTADO);
        app.ctx.voiceService().awaitIdle(Duration.ofSeconds(5));
        assertThat(app.speech.spoken()).anySatisfy(s -> assertThat(s).contains("Gmail"));
    }

    @Test
    void sinAlertaDeVozSiSeDesactivaOSiNoVeniaDeConectado() throws Exception {
        ConnectorState ok = state(ConnectorType.ANDROID_WEBHOOK, "android-principal", ConnectorStatus.CONECTADO, "ok");
        ConnectorState down = ok.withStatus(ConnectorStatus.ERROR, "falló", app.clock.instant());
        monitor(false).onStateChanged(ok, down);
        // venía de REQUIERE_AUTORIZACION (arranque sin credenciales): se publica pero no se habla
        ConnectorState auth = state(ConnectorType.GMAIL, "gmail-principal", ConnectorStatus.REQUIERE_AUTORIZACION, "sin credenciales");
        monitor(true).onStateChanged(null, auth);
        app.ctx.voiceService().awaitIdle(Duration.ofSeconds(5));
        assertThat(app.speech.spoken()).noneSatisfy(s -> assertThat(s).contains("Atención"));
        assertThat(published).hasSize(2);
    }
}
