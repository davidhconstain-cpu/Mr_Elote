package com.vivanetpay.integration.gmail;

import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.UrlEncodedContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.vivanetpay.security.SecretStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * OAuth 2.0 de "app instalada" con PKCE y redirección loopback (127.0.0.1, puerto aleatorio). Alcance único:
 * {@code gmail.readonly}. Los tokens viven solo en {@link SecretStore}. El client_id/secret se leen de un
 * archivo externo (descargado de Google Cloud), nunca del repositorio.
 */
public final class GoogleGmailAuth implements GmailAuth {

    /** Único alcance solicitado (RF-021, RNF-004). */
    public static final String SCOPE = GmailScopes.GMAIL_READONLY;
    static final String USER_ID = "vivanet";
    static final String APPLICATION_NAME = "Vivanet Pay";
    private static final String REVOKE_URL = "https://oauth2.googleapis.com/revoke";
    private static final long AUTH_TIMEOUT_MINUTES = 5;
    private static final Logger log = LoggerFactory.getLogger(GoogleGmailAuth.class);

    private final Path clientSecretsPath;
    private final SecretStoreDataStoreFactory dataStore;
    private final JsonFactory json = GsonFactory.getDefaultInstance();
    private HttpTransport transport;

    public GoogleGmailAuth(Path clientSecretsPath, SecretStore secrets, String connectorId) {
        this.clientSecretsPath = clientSecretsPath;
        this.dataStore = new SecretStoreDataStoreFactory(secrets, "gmail." + connectorId + ".");
    }

    private synchronized HttpTransport transport() throws IOException {
        if (transport == null) {
            try {
                transport = GoogleNetHttpTransport.newTrustedTransport();
            } catch (GeneralSecurityException e) {
                throw new IOException("No se pudo inicializar la conexión segura", e);
            }
        }
        return transport;
    }

    private AuthorizationCodeFlow flow() throws IOException {
        if (clientSecretsPath == null || !Files.isRegularFile(clientSecretsPath)) {
            throw new GmailAuthException("Falta el archivo de credenciales de Google (client_secret.json). "
                    + "Configura gmail.clientSecretsPath; consulta docs/GMAIL_OAUTH.md.");
        }
        GoogleClientSecrets secrets;
        try (Reader r = Files.newBufferedReader(clientSecretsPath, StandardCharsets.UTF_8)) {
            secrets = GoogleClientSecrets.load(json, r);
        } catch (IOException | RuntimeException e) {
            throw new GmailAuthException("El archivo de credenciales de Google no es válido. "
                    + "Descárgalo de nuevo desde Google Cloud (cliente 'App de escritorio').");
        }
        if (secrets.getDetails() == null || secrets.getDetails().getClientId() == null) {
            throw new GmailAuthException("El archivo de credenciales de Google no es válido. "
                    + "Descárgalo de nuevo desde Google Cloud (cliente 'App de escritorio').");
        }
        return new GoogleAuthorizationCodeFlow.Builder(transport(), json, secrets, List.of(SCOPE))
                .setDataStoreFactory(dataStore)
                .setAccessType("offline")
                .enablePKCE()
                .build();
    }

    private Credential loadCredential(AuthorizationCodeFlow flow) throws IOException {
        Credential c = flow.loadCredential(USER_ID);
        if (c == null || (c.getRefreshToken() == null && c.getAccessToken() == null)) {
            throw new GmailAuthException("Gmail no está autorizado. Pulsa 'Autorizar Gmail'.");
        }
        return c;
    }

    @Override
    public boolean hasCredentials() {
        try {
            var store = dataStore.<com.google.api.client.auth.oauth2.StoredCredential>getDataStore("StoredCredential");
            var c = store.get(USER_ID);
            return c != null && c.getRefreshToken() != null;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    @Override
    public GmailClient authorize() throws IOException {
        AuthorizationCodeFlow flow = flow();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setHost("127.0.0.1").setPort(-1).build();
        ExecutorService ex = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "gmail-oauth");
            t.setDaemon(true);
            return t;
        });
        Credential credential;
        try {
            Future<Credential> f = ex.submit(() -> new AuthorizationCodeInstalledApp(flow, receiver).authorize(USER_ID));
            credential = f.get(AUTH_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        } catch (TimeoutException e) {
            throw new GmailAuthException("La autorización no se completó a tiempo. Inténtalo de nuevo.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GmailAuthException("Autorización interrumpida.");
        } catch (ExecutionException e) {
            log.warn("Autorización de Gmail fallida: {}", e.getCause() == null ? "?" : e.getCause().getClass().getSimpleName());
            throw new GmailAuthException("No se pudo completar la autorización de Gmail. Inténtalo de nuevo.");
        } finally {
            try {
                receiver.stop();
            } catch (IOException ignored) {
                // nada que hacer
            }
            ex.shutdownNow();
        }
        return client(credential);
    }

    @Override
    public GmailClient openClient() throws IOException {
        AuthorizationCodeFlow flow = flow();
        return client(loadCredential(flow));
    }

    private GmailClient client(Credential credential) throws IOException {
        Gmail gmail = new Gmail.Builder(transport(), json, credential).setApplicationName(APPLICATION_NAME).build();
        return new GoogleGmailClient(gmail);
    }

    @Override
    public void revoke() throws IOException {
        IOException remoteFailure = null;
        try {
            var store = dataStore.<com.google.api.client.auth.oauth2.StoredCredential>getDataStore("StoredCredential");
            var c = store.get(USER_ID);
            String token = c == null ? null : (c.getRefreshToken() != null ? c.getRefreshToken() : c.getAccessToken());
            if (token != null) {
                try {
                    HttpRequest req = transport().createRequestFactory().buildPostRequest(new GenericUrl(REVOKE_URL),
                            new UrlEncodedContent(Map.of("token", token)));
                    req.setThrowExceptionOnExecuteError(false);
                    HttpResponse resp = req.execute();
                    int status = resp.getStatusCode();
                    resp.disconnect();
                    // 400 invalid_token: ya estaba revocado; se considera correcto
                    if (status != 200 && status != 400) {
                        remoteFailure = new IOException("Google respondió " + status + " al revocar");
                    }
                } catch (IOException e) {
                    remoteFailure = e;
                }
            }
        } finally {
            dataStore.deleteAll();
        }
        if (remoteFailure != null) {
            throw remoteFailure;
        }
    }
}
