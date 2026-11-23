package com.vivanetpay.integration.gmail;

import com.google.api.client.auth.oauth2.StoredCredential;
import com.google.api.client.util.store.DataStore;
import com.vivanetpay.integration.gmail.GmailTestSupport.MemSecrets;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    // ---- QA: deserialización Java (filtro de clases estricto) ----

    /** "Gadget" de prueba: si se llegara a deserializar, ejecuta código en readObject. */
    static final class Gadget implements Serializable {
        static final AtomicInteger EXECUTED = new AtomicInteger();

        private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            EXECUTED.incrementAndGet();
        }
    }

    private static String serializedValue(Object o) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(o);
        }
        return Base64.getEncoder().encodeToString(bos.toByteArray());
    }

    private static DataStore<StoredCredential> storeWith(MemSecrets secrets, Object payload) throws IOException {
        String json = "{\"vivanet\":\"" + serializedValue(payload) + "\"}";
        secrets.put("gmail.gmail-principal.datastore.StoredCredential", json.toCharArray());
        return new SecretStoreDataStoreFactory(secrets, "gmail.gmail-principal.")
                .<StoredCredential>getDataStore("StoredCredential");
    }

    @Test
    void filtroRechazaCualquierClaseFueraDeLaListaBlanca() throws IOException {
        Gadget.EXECUTED.set(0);
        List<Object> hostile = new ArrayList<>();
        hostile.add(new Gadget());
        hostile.add(new HashMap<>(Map.of("a", "b")));
        hostile.add(new ArrayList<>(List.of("x")));
        hostile.add(new LinkedHashSet<>(List.of("x")));
        hostile.add(new java.util.PriorityQueue<>(List.of("x")));
        hostile.add(java.net.URI.create("http://127.0.0.1:1/x").toURL());
        hostile.add(java.util.concurrent.TimeUnit.SECONDS);
        hostile.add(Integer.valueOf(7));
        hostile.add(new java.io.File("/etc/passwd"));
        hostile.add(new Object[]{new Gadget()});
        hostile.add(new RuntimeException("x"));
        for (Object o : hostile) {
            DataStore<StoredCredential> store = storeWith(new MemSecrets(), o);
            assertThatThrownBy(() -> store.get("vivanet")).as(o.getClass().getName())
                    .isInstanceOf(java.io.InvalidClassException.class);
        }
        assertThat(Gadget.EXECUTED.get()).as("ningún readObject ajeno debe ejecutarse").isZero();
    }

    @Test
    void filtroLimitaTamanoYProfundidad() throws IOException {
        StoredCredential big = new StoredCredential();
        big.setAccessToken("x".repeat(20_000)); // > maxbytes del filtro
        DataStore<StoredCredential> store = storeWith(new MemSecrets(), big);
        assertThatThrownBy(() -> store.get("vivanet")).isInstanceOf(java.io.InvalidClassException.class);
        assertThat(SecretStoreDataStoreFactory.FILTER_PATTERN).endsWith("!*").doesNotContain("java.util.*")
                .doesNotContain("java.lang.*");
    }

    @Test
    void credencialLegitimaSigueFuncionandoConTokensRealistas() throws IOException {
        StoredCredential c = new StoredCredential();
        c.setAccessToken("ya29." + "A".repeat(180));
        c.setRefreshToken("1//" + "B".repeat(100));
        c.setExpirationTimeMilliseconds(Long.MAX_VALUE);
        StoredCredential back = storeWith(new MemSecrets(), c).get("vivanet");
        assertThat(back.getAccessToken()).isEqualTo(c.getAccessToken());
        assertThat(back.getRefreshToken()).isEqualTo(c.getRefreshToken());
    }
}
