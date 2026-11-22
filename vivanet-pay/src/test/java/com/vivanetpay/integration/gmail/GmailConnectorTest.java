package com.vivanetpay.integration.gmail;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.vivanetpay.application.EventSink;
import com.vivanetpay.domain.ConnectorStatus;
import com.vivanetpay.domain.RawEvent;
import com.vivanetpay.integration.ConnectorDependencies;
import com.vivanetpay.integration.gmail.GmailConnector.SyncOutcome;
import com.vivanetpay.integration.gmail.GmailTestSupport.*;
import com.vivanetpay.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GmailConnectorTest {
    static final String NEQUI = "Nequi <no-reply@nequi.com.co>";
    static final String SPAMMER = "Promo <ofertas@tienda.example>";

    FakeClient client;
    FakeAuth auth;
    RecordingSink sink;
    MemCursors cursors;
    MemSecrets secrets;
    MutableClock clock;
    GmailConnector connector;

    @BeforeEach
    void setUp() {
        client = new FakeClient();
        auth = new FakeAuth(client);
        sink = new RecordingSink();
        cursors = new MemCursors();
        secrets = new MemSecrets();
        clock = new MutableClock(Instant.parse("2025-11-24T15:00:00Z"));
        connector = newConnector(sink);
    }

    GmailConnector newConnector(EventSink s) {
        ConnectorDependencies deps = new ConnectorDependencies("gmail-principal", s, cursors, secrets, Map.of(),
                Path.of("target-gmail/data"), clock);
        return new GmailConnector(deps, GmailSettings.from(Map.of(), deps.dataDir()), auth, () -> 0.5);
    }

    @Test
    void firstSyncMarksEventsHistoricalAndCommitsHistoryId() {
        client.listResult.addAll(List.of("m2", "m1")); // más reciente primero
        client.mailbox.put("m1", GmailTestSupport.message("m1", NEQUI, "Recibiste dinero", "Te llegaron $10.000"));
        client.mailbox.put("m2", GmailTestSupport.message("m2", NEQUI, "Recibiste dinero", "Te llegaron $20.000"));

        assertThat(connector.syncOnce()).isEqualTo(SyncOutcome.OK);

        assertThat(sink.received).extracting(RawEvent::externalEventId).containsExactly("m1", "m2");
        assertThat(sink.received).allMatch(RawEvent::historical);
        assertThat(cursors.loadCursor("gmail-principal")).contains("100");
        assertThat(connector.state().status()).isEqualTo(ConnectorStatus.CONECTADO);
        assertThat(connector.lastSuccessAt()).contains(clock.instant());
        assertThat(connector.authorizedAccount()).contains("titular@example.com");
    }

    @Test
    void incrementalSyncIngestsNewMessagesWithHeadersAndSkipsOtherSenders() {
        cursors.commitCursor("gmail-principal", "100");
        client.historyId = "120";
        client.historyAdded.addAll(List.of("n1", "x1"));
        client.mailbox.put("n1", GmailTestSupport.message("n1", NEQUI, "=?UTF-8?B?wqFSZWNpYmlzdGUgcGxhdGEh?=",
                "Te llegaron $50.000"));
        client.mailbox.put("x1", GmailTestSupport.message("x1", SPAMMER, "Ofertas", "compra ya"));

        assertThat(connector.syncOnce()).isEqualTo(SyncOutcome.OK);

        assertThat(sink.received).hasSize(1);
        RawEvent e = sink.received.get(0);
        assertThat(e.externalEventId()).isEqualTo("n1");
        assertThat(e.historical()).isFalse();
        assertThat(e.subject()).isEqualTo("¡Recibiste plata!");
        assertThat(e.sender()).isEqualTo(NEQUI);
        assertThat(e.rawText()).isEqualTo("Te llegaron $50.000");
        assertThat(e.sourceTimestamp()).isEqualTo(Instant.parse("2025-11-24T15:15:30Z"));
        assertThat(e.headers()).containsKeys("Message-ID", "Authentication-Results", "Date", "From");
        assertThat(e.headers().get("Authentication-Results")).contains("dkim=pass");
        assertThat(client.fullFetches).isEqualTo(1); // no se descargó el cuerpo del otro remitente
        assertThat(cursors.loadCursor("gmail-principal")).contains("120");
    }

    @Test
    void cursorDoesNotAdvanceWhenIngestFailsAndRecoversOnRetry() {
        cursors.commitCursor("gmail-principal", "100");
        client.historyId = "130";
        client.historyAdded.add("n1");
        client.mailbox.put("n1", GmailTestSupport.message("n1", NEQUI, "Pago", "Te llegaron $1.000"));
        sink.failing = true;

        assertThat(connector.syncOnce()).isEqualTo(SyncOutcome.ERROR);
        assertThat(cursors.loadCursor("gmail-principal")).contains("100");
        assertThat(connector.state().status()).isEqualTo(ConnectorStatus.ERROR);

        sink.failing = false;
        assertThat(connector.syncOnce()).isEqualTo(SyncOutcome.OK);
        assertThat(cursors.loadCursor("gmail-principal")).contains("130");
        assertThat(sink.persisted).containsKey("n1");
    }

    @Test
    void repeatedMessageIsNotDuplicated() {
        cursors.commitCursor("gmail-principal", "100");
        client.historyId = "110";
        client.historyAdded.add("n1");
        client.mailbox.put("n1", GmailTestSupport.message("n1", NEQUI, "Pago", "Te llegaron $1.000"));

        connector.syncOnce();
        connector.syncOnce(); // la API vuelve a devolver n1 (superposición de historial)
        // incluso un conector reiniciado (sin memoria) delega la idempotencia en el sink
        GmailConnector restarted = newConnector(sink);
        cursors.commitCursor("gmail-principal", "100");
        restarted.syncOnce();

        assertThat(sink.persisted).hasSize(1);
        assertThat(sink.received.stream().filter(e -> e.externalEventId().equals("n1")).count()).isEqualTo(2);
    }

    @Test
    void expiredHistoryIdTriggersBoundedResyncMarkedHistorical() {
        cursors.commitCursor("gmail-principal", "5");
        client.historyExpired = true;
        client.historyId = "900";
        client.listResult.add("r1");
        client.mailbox.put("r1", GmailTestSupport.message("r1", NEQUI, "Pago", "Te llegaron $3.000"));

        assertThat(connector.syncOnce()).isEqualTo(SyncOutcome.OK);

        assertThat(sink.received).hasSize(1);
        assertThat(sink.received.get(0).historical()).isTrue();
        assertThat(cursors.loadCursor("gmail-principal")).contains("900");
    }

    @Test
    void invalidGrantRequiresAuthorizationWithoutBlockingAndAuthorizeRecovers() {
        cursors.commitCursor("gmail-principal", "100");
        client.failures.add(new GmailAuthException("invalid_grant"));

        assertThat(connector.syncOnce()).isEqualTo(SyncOutcome.AUTH_REQUIRED);
        assertThat(connector.state().status()).isEqualTo(ConnectorStatus.REQUIERE_AUTORIZACION);
        assertThat(connector.isAuthorized()).isFalse();

        int calls = client.apiCalls;
        assertThat(connector.syncOnce()).isEqualTo(SyncOutcome.AUTH_REQUIRED); // no martilla la API
        assertThat(client.apiCalls).isEqualTo(calls);

        connector.authorize();
        assertThat(connector.isAuthorized()).isTrue();
        assertThat(connector.syncOnce()).isEqualTo(SyncOutcome.OK);
        assertThat(connector.state().status()).isEqualTo(ConnectorStatus.CONECTADO);
    }

    @Test
    void missingCredentialsReportsRequiresAuthorization() {
        auth.credentials = false;
        assertThat(connector.syncOnce()).isEqualTo(SyncOutcome.AUTH_REQUIRED);
        assertThat(connector.state().status()).isEqualTo(ConnectorStatus.REQUIERE_AUTORIZACION);
        assertThat(sink.received).isEmpty();
    }

    @Test
    void networkErrorDisconnectsThenRecovers() {
        cursors.commitCursor("gmail-principal", "100");
        client.failures.add(new UnknownHostException("gmail.googleapis.com"));

        assertThat(connector.syncOnce()).isEqualTo(SyncOutcome.NETWORK_ERROR);
        assertThat(connector.state().status()).isEqualTo(ConnectorStatus.DESCONECTADO);
        assertThat(connector.state().lastSuccessAt()).isNull();
        assertThat(connector.nextDelayMillis()).isBetween(4_000L, 6_000L); // backoff, no el sondeo normal

        clock.advance(java.time.Duration.ofMinutes(1));
        assertThat(connector.syncOnce()).isEqualTo(SyncOutcome.OK);
        assertThat(connector.state().status()).isEqualTo(ConnectorStatus.CONECTADO);
        assertThat(connector.state().lastSuccessAt()).isEqualTo(clock.instant());
        assertThat(connector.nextDelayMillis()).isEqualTo(60_000L);
    }

    @Test
    void rateLimitUsesLongerBackoff() {
        cursors.commitCursor("gmail-principal", "100");
        client.failures.add(new GmailRateLimitException("429"));
        assertThat(connector.syncOnce()).isEqualTo(SyncOutcome.RATE_LIMITED);
        assertThat(connector.nextDelayMillis()).isGreaterThanOrEqualTo(24_000L);
    }

    @Test
    void backoffGrowsAndIsCapped() {
        long d1 = GmailConnector.backoffMillis(1, 5_000, 900_000, 0.5);
        long d4 = GmailConnector.backoffMillis(4, 5_000, 900_000, 0.5);
        long d50 = GmailConnector.backoffMillis(50, 5_000, 900_000, 0.99);
        assertThat(d1).isEqualTo(5_000);
        assertThat(d4).isEqualTo(40_000);
        assertThat(d50).isLessThanOrEqualTo(900_000);
        assertThat(GmailConnector.backoffMillis(3, 5_000, 900_000, 0.0)).isLessThan(GmailConnector.backoffMillis(3, 5_000, 900_000, 1.0));
    }

    @Test
    void revokeClearsStateAndExplainsManualRevocation() {
        cursors.commitCursor("gmail-principal", "100");
        connector.syncOnce();
        connector.revoke();

        assertThat(auth.revoked).isTrue();
        assertThat(connector.state().status()).isEqualTo(ConnectorStatus.REQUIERE_AUTORIZACION);
        assertThat(connector.state().detail()).contains("myaccount.google.com/permissions");
        assertThat(connector.isAuthorized()).isFalse();
        assertThat(connector.authorizedAccount()).isEmpty();
        assertThat(cursors.loadCursor("gmail-principal")).contains("");
        assertThat(secrets.map).isEmpty();
        assertThat(connector.syncOnce()).isEqualTo(SyncOutcome.AUTH_REQUIRED);
    }

    @Test
    void revokeWhenGoogleUnreachableStillClearsLocalAndReports() {
        auth.revokeFailure = new IOException("sin red");
        assertThatThrownBy(() -> connector.revoke()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("myaccount.google.com/permissions");
        assertThat(connector.state().status()).isEqualTo(ConnectorStatus.REQUIERE_AUTORIZACION);
    }

    @Test
    void pausedConnectorDoesNotSync() {
        connector.pause();
        assertThat(connector.syncOnce()).isEqualTo(SyncOutcome.SKIPPED);
        assertThat(client.apiCalls).isZero();
    }

    @Test
    void logsAndStatusNeverContainTokens() {
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        try {
            cursors.commitCursor("gmail-principal", "100");
            String leak = "ya29.A0SECRETACCESSTOKEN refresh_token=1//0gSECRETREFRESH";
            client.failures.add(new IOException(leak));
            connector.syncOnce();
            client.failures.add(new GmailAuthException("sesión: " + leak));
            connector.syncOnce();
            connector.authorize();
            client.runtimeFailure = new IllegalStateException(leak);
            connector.syncOnce();

            StringBuilder all = new StringBuilder(connector.state().detail());
            for (ILoggingEvent ev : appender.list) {
                all.append(ev.getFormattedMessage());
                if (ev.getThrowableProxy() != null) {
                    all.append(ev.getThrowableProxy().getMessage());
                }
            }
            assertThat(all.toString()).doesNotContain("SECRETACCESSTOKEN").doesNotContain("SECRETREFRESH");
        } finally {
            root.detachAppender(appender);
        }
    }
}
