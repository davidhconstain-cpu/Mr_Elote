package com.vivanetpay.integration.gmail;

import com.google.api.client.auth.oauth2.StoredCredential;
import com.google.api.client.util.store.DataStore;
import com.vivanetpay.integration.gmail.GmailTestSupport.MemSecrets;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GmailOAuthSupportTest {

    @Test
    void scopeIsOnlyReadonly() {
        assertThat(GoogleGmailAuth.SCOPE).isEqualTo("https://www.googleapis.com/auth/gmail.readonly");
    }

    @Test
    void dataStoreKeepsTokensInSecretStoreOnlyAndRoundTrips() throws IOException {
        MemSecrets secrets = new MemSecrets();
        SecretStoreDataStoreFactory factory = new SecretStoreDataStoreFactory(secrets, "gmail.gmail-principal.");
        DataStore<StoredCredential> store = factory.getDataStore("StoredCredential");
        StoredCredential c = new StoredCredential();
        c.setAccessToken("ya29.access");
        c.setRefreshToken("1//refresh");
        c.setExpirationTimeMilliseconds(123L);
        store.set("vivanet", c);

        assertThat(secrets.map).containsOnlyKeys("gmail.gmail-principal.datastore.StoredCredential");

        // nueva fábrica = reinicio de la aplicación
        StoredCredential back = new SecretStoreDataStoreFactory(secrets, "gmail.gmail-principal.")
                .<StoredCredential>getDataStore("StoredCredential").get("vivanet");
        assertThat(back.getRefreshToken()).isEqualTo("1//refresh");
        assertThat(back.getAccessToken()).isEqualTo("ya29.access");
        assertThat(back.getExpirationTimeMilliseconds()).isEqualTo(123L);

        factory.deleteAll();
        assertThat(secrets.map).isEmpty();
    }

    @Test
    void errorClassification() {
        IOException o = new IOException("x");
        assertThat(GmailErrors.classify(400, null, "invalid_grant", false, o)).isInstanceOf(GmailAuthException.class);
        assertThat(GmailErrors.classify(401, null, null, false, o)).isInstanceOf(GmailAuthException.class);
        assertThat(GmailErrors.classify(403, "rateLimitExceeded", null, false, o)).isInstanceOf(GmailRateLimitException.class);
        assertThat(GmailErrors.classify(403, "userRateLimitExceeded", null, false, o)).isInstanceOf(GmailRateLimitException.class);
        assertThat(GmailErrors.classify(429, null, null, false, o)).isInstanceOf(GmailRateLimitException.class);
        assertThat(GmailErrors.classify(403, "insufficientPermissions", null, false, o)).isInstanceOf(GmailAuthException.class);
        assertThat(GmailErrors.classify(404, "notFound", null, true, o)).isInstanceOf(GmailHistoryExpiredException.class);
        assertThat(GmailErrors.classify(503, null, null, false, o)).isSameAs(o);
    }

    @Test
    void settingsDefaultsAndSenderFilter() {
        GmailSettings s = GmailSettings.from(Map.of(), Path.of("data"));
        assertThat(s.pollInterval().toSeconds()).isEqualTo(60);
        assertThat(s.query()).contains("nequi.com.co").contains("newer_than:2d");
        assertThat(s.clientSecretsPath()).isEqualTo(Path.of("data/gmail/client_secret.json"));
        assertThat(s.senderAllowed("Nequi <no-reply@nequi.com.co>")).isTrue();
        assertThat(s.senderAllowed("alertas@notificaciones.bancolombia.com.co")).isTrue();
        assertThat(s.senderAllowed("x@evilnequi.com.co")).isFalse();
        assertThat(s.senderAllowed("x@nequi.com.co.evil.com")).isFalse();
        GmailSettings c = GmailSettings.from(Map.of("gmail.pollSeconds", "30", "clientSecretsPath", "/tmp/cs.json",
                "query", "from:x", "senders", "banco.example"), Path.of("data"));
        assertThat(c.pollInterval().toSeconds()).isEqualTo(30);
        assertThat(c.query()).isEqualTo("from:x");
        assertThat(c.senderAllowed("a@banco.example")).isTrue();
    }
}
