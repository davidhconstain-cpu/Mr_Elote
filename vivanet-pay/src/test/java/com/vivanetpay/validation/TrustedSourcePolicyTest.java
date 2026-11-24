package com.vivanetpay.validation;

import com.vivanetpay.application.IngestionResult;
import com.vivanetpay.domain.Actors;
import com.vivanetpay.domain.AuditEntry;
import com.vivanetpay.domain.EventSource;
import com.vivanetpay.domain.EventStatus;
import com.vivanetpay.domain.RawEvent;
import com.vivanetpay.domain.Validation;
import com.vivanetpay.support.TestApp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** DP-01 decidida: confirmación automática por fuentes verificadas (parsers reales, política por defecto). */
class TrustedSourcePolicyTest {
    static final String AUTH_OK = "mx.google.com; dkim=pass header.i=@nequi.com.co header.s=s1 header.b=abc; "
            + "spf=pass (google.com: domain of no-responder@nequi.com.co designates 1.2.3.4 as permitted sender) "
            + "smtp.mailfrom=no-responder@nequi.com.co; dmarc=pass (p=REJECT sp=REJECT dis=NONE) header.from=nequi.com.co";
    static final String NEQUI_MAIL = "Hola. JUAN PEREZ te envió $50.000 a tu Nequi.";

    @TempDir
    Path dir;
    TestApp app;

    @AfterEach
    void tearDown() {
        if (app != null) {
            app.ctx.close();
        }
    }

    private RawEvent android(String id, String text, String pkg, boolean authenticated) {
        return RawEvent.builder(EventSource.ANDROID_NEQUI, "android-principal").externalEventId(id)
                .sourceTimestamp(app.clock.instant()).receivedAt(app.clock.instant()).sender(pkg)
                .headers(authenticated ? Map.of("webhook.auth", "token") : Map.of()).rawText(text).build();
    }

    private RawEvent gmail(String id, String text, String from, String authResults, boolean historical) {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("From", from);
        if (authResults != null) {
            h.put("Authentication-Results", authResults);
        }
        return RawEvent.builder(EventSource.GMAIL, "gmail-principal").externalEventId(id)
                .sourceTimestamp(app.clock.instant()).receivedAt(app.clock.instant()).subject("Recibiste dinero")
                .sender(from).headers(h).rawText(text).historical(historical).build();
    }

    private IngestionResult ingest(RawEvent e) {
        return app.ctx.ingestionService().ingest(e);
    }

    @Test
    void nequiValidoSeConfirmaConVozPagoRecibidoYRegistroDeSistema() throws Exception {
        app = TestApp.createReal(dir, Map.of());
        IngestionResult r = ingest(android("a-1", "JUAN PÉREZ te envió $50.000", "com.nequi.MobileApp", true));
        assertThat(r.status()).isEqualTo(EventStatus.VALIDADO_POR_FUENTE);
        assertThat(app.ctx.voiceService().awaitIdle(Duration.ofSeconds(5))).isTrue();
        assertThat(app.speech.spoken()).containsExactly("Pago recibido por 50.000 pesos desde Nequi.");
        assertThat(app.ctx.dashboardService().today().confirmedTotal()).isEqualTo(50_000L);
        assertThat(app.ctx.dashboardService().today().unconfirmedTotal()).isZero();

        List<Validation> vals = app.ctx.validationRepository().findByEvent(r.eventId());
        assertThat(vals).anySatisfy(v -> {
            assertThat(v.rule()).isEqualTo("POLITICA:" + TrustedSourceValidationPolicy.RULE_ANDROID_NEQUI);
            assertThat(v.actor()).isEqualTo(Actors.SYSTEM);
        });
        assertThat(app.ctx.auditRepository().findByEntity(AuditEntry.ENTITY_EVENT, Long.toString(r.eventId())))
                .anySatisfy(a -> {
                    assertThat(a.actor()).isEqualTo(Actors.SYSTEM);
                    assertThat(a.afterValue()).isEqualTo("VALIDADO_POR_FUENTE");
                    assertThat(a.reason()).contains(TrustedSourceValidationPolicy.RULE_ANDROID_NEQUI);
                });
    }

    @Test
    void nequiSinWebhookAutenticadoOConPaqueteAjenoOBajaConfianzaQuedaPorVerificar() throws Exception {
        app = TestApp.createReal(dir, Map.of());
        assertThat(ingest(android("a-1", "JUAN PÉREZ te envió $50.000", "com.nequi.MobileApp", false)).status())
                .as("sin la marca del webhook autenticado").isEqualTo(EventStatus.PARSEADO);
        app.clock.advance(Duration.ofHours(1));
        IngestionResult other = ingest(android("a-2", "ANA te envió $20.000", "com.otra.app", true));
        assertThat(other.status()).as("paquete que no es de Nequi: confianza baja").isNotIn(EventStatus.VALIDADO_POR_FUENTE, EventStatus.ANUNCIADO);
        assertThat(other.status().isConfirmed()).isFalse();
        app.clock.advance(Duration.ofHours(1));
        assertThat(ingest(android("a-3", "Mensaje raro sin plantilla $5.000", "com.nequi.MobileApp", true)).status().isConfirmed()).isFalse();
        assertThat(app.ctx.voiceService().awaitIdle(Duration.ofSeconds(5))).isTrue();
        assertThat(app.speech.spoken()).noneMatch(t -> t.startsWith("Pago recibido"));
        assertThat(app.ctx.dashboardService().today().confirmedTotal()).isZero();
    }

    @Test
    void gmailConDkimPassYDominioEsperadoSeConfirma() throws Exception {
        app = TestApp.createReal(dir, Map.of());
        IngestionResult r = ingest(gmail("g-1", NEQUI_MAIL, "Nequi <no-responder@nequi.com.co>", AUTH_OK, false));
        assertThat(r.status()).isEqualTo(EventStatus.VALIDADO_POR_FUENTE);
        assertThat(app.ctx.validationRepository().findByEvent(r.eventId()))
                .anySatisfy(v -> assertThat(v.rule()).isEqualTo("POLITICA:" + TrustedSourceValidationPolicy.RULE_GMAIL));
        assertThat(app.ctx.voiceService().awaitIdle(Duration.ofSeconds(5))).isTrue();
        assertThat(app.speech.spoken()).containsExactly("Pago recibido por 50.000 pesos desde Nequi.");
    }

    @Test
    void gmailSinCabeceraODkimFailODominioAjenoOServidorFalsoNoSeConfirma() {
        app = TestApp.createReal(dir, Map.of());
        String from = "Nequi <no-responder@nequi.com.co>";
        int i = 0;
        for (String auth : new String[]{null, "mx.google.com; dkim=fail header.i=@nequi.com.co; spf=pass smtp.mailfrom=a@nequi.com.co",
                "mx.google.com; dkim=pass header.i=@evil.com; spf=pass smtp.mailfrom=a@evil.com; dmarc=pass header.from=evil.com",
                "mx.google.com; dkim=pass header.i=@nequi.com.co; spf=fail smtp.mailfrom=a@nequi.com.co; dmarc=fail header.from=nequi.com.co",
                "mx.google.com; dkim=pass header.i=@nequi.com.co",
                // cabecera inventada por el remitente (el identificador del servidor no es de Google)
                "atacante.example; dkim=pass header.i=@nequi.com.co; spf=pass smtp.mailfrom=a@nequi.com.co; dmarc=pass header.from=nequi.com.co"}) {
            IngestionResult r = ingest(gmail("g-" + i++, NEQUI_MAIL + " " + i, from, auth, false));
            assertThat(r.status().isConfirmed()).as("auth=" + auth).isFalse();
            app.clock.advance(Duration.ofHours(1));
        }
        // dominio del remitente falso aunque la autenticación sea buena
        IngestionResult spoof = ingest(gmail("g-x", NEQUI_MAIL + " x", "Nequi <no-responder@nequi.com.co.evil.com>", AUTH_OK, false));
        assertThat(spoof.status().isConfirmed()).isFalse();
        assertThat(app.ctx.dashboardService().today().confirmedTotal()).isZero();
    }

    @Test
    void duplicadoEntreFuentesVaARevisionSinDobleConteo() throws Exception {
        app = TestApp.createReal(dir, Map.of());
        IngestionResult first = ingest(android("a-1", "JUAN PEREZ te envió $50.000", "com.nequi.MobileApp", true));
        assertThat(first.status()).isEqualTo(EventStatus.VALIDADO_POR_FUENTE);
        app.clock.advance(Duration.ofMinutes(2));
        IngestionResult second = ingest(gmail("g-1", NEQUI_MAIL, "Nequi <no-responder@nequi.com.co>", AUTH_OK, false));
        assertThat(second.status()).isEqualTo(EventStatus.PENDIENTE_REVISION);
        assertThat(second.reason()).containsIgnoringCase("duplicado");
        var today = app.ctx.dashboardService().today();
        assertThat(today.confirmedTotal()).as("solo el primero cuenta como confirmado").isEqualTo(50_000L);
        assertThat(today.confirmedCount()).isEqualTo(1);
        assertThat(app.ctx.voiceService().awaitIdle(Duration.ofSeconds(5))).isTrue();
        assertThat(app.speech.spoken()).filteredOn(t -> t.startsWith("Pago recibido")).hasSize(1);
    }

    @Test
    void conAutomaticValidationFalseVuelveAlComportamientoConservador() throws Exception {
        app = TestApp.createReal(dir, Map.of("policy.automaticValidation", "false"));
        IngestionResult r = ingest(android("a-1", "JUAN PÉREZ te envió $50.000", "com.nequi.MobileApp", true));
        assertThat(r.status()).isEqualTo(EventStatus.PARSEADO);
        assertThat(ingest(gmail("g-1", NEQUI_MAIL + " b", "Nequi <no-responder@nequi.com.co>", AUTH_OK, false)).status().isConfirmed()).isFalse();
        assertThat(app.ctx.voiceService().awaitIdle(Duration.ofSeconds(5))).isTrue();
        assertThat(app.speech.spoken()).isNotEmpty().allMatch(t -> t.startsWith("Nueva notificación"));
        assertThat(app.ctx.dashboardService().today().confirmedTotal()).isZero();
    }

    @Test
    void elInterruptorSeAplicaEnCalienteYQuedaAuditado() throws Exception {
        app = TestApp.createReal(dir, Map.of());
        assertThat(app.ctx.automaticValidationEnabled()).isTrue();
        app.ctx.setAutomaticValidation(false, "dueño");
        assertThat(app.ctx.automaticValidationEnabled()).isFalse();
        assertThat(app.ctx.config().getBoolean("policy.automaticValidation", true)).isFalse();
        assertThat(ingest(android("a-1", "JUAN PÉREZ te envió $50.000", "com.nequi.MobileApp", true)).status())
                .isEqualTo(EventStatus.PARSEADO);
        app.ctx.setAutomaticValidation(true, "dueño");
        app.clock.advance(Duration.ofHours(1));
        assertThat(ingest(android("a-2", "LUIS MORA te envió $20.000", "com.nequi.MobileApp", true)).status())
                .isEqualTo(EventStatus.VALIDADO_POR_FUENTE);
        assertThat(app.ctx.auditRepository().findByEntity(AuditEntry.ENTITY_SETTING, "policy.automaticValidation"))
                .hasSize(2).allSatisfy(a -> assertThat(a.actor()).isEqualTo("dueño"));
    }

    @Test
    void historicoPuedeQuedarConfirmadoPeroNuncaSeAnuncia() throws Exception {
        app = TestApp.createReal(dir, Map.of());
        IngestionResult r = ingest(gmail("g-h", NEQUI_MAIL, "Nequi <no-responder@nequi.com.co>", AUTH_OK, true));
        assertThat(r.status()).isEqualTo(EventStatus.VALIDADO_POR_FUENTE);
        assertThat(app.ctx.voiceService().awaitIdle(Duration.ofSeconds(5))).isTrue();
        assertThat(app.speech.spoken()).isEmpty();
    }

    @Test
    void sinTrustedSourcesDeclaradasUsaLosValoresPorDefecto() {
        app = TestApp.createReal(dir, Map.of());
        var c = app.ctx.validationPolicy().config();
        assertThat(c.automaticValidationEnabled()).isTrue();
        assertThat(c.trustedSources()).containsExactlyInAnyOrder(EventSource.ANDROID_NEQUI, EventSource.GMAIL);
        assertThat(app.ctx.validationPolicy().id()).isEqualTo(TrustedSourceValidationPolicy.ID);
    }
}
