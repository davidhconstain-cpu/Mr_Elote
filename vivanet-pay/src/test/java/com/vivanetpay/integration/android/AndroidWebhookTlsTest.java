package com.vivanetpay.integration.android;

import com.vivanetpay.domain.ConnectorStatus;
import com.vivanetpay.integration.ConnectorDependencies;
import com.vivanetpay.security.InMemorySecretStore;
import com.vivanetpay.support.TestApp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** HTTPS opcional con keystore PKCS12 (generado con keytool del JDK). */
class AndroidWebhookTlsTest {
    @TempDir
    Path dir;
    private TestApp app;
    private AndroidWebhookConnector connector;

    @AfterEach
    void tearDown() {
        if (connector != null) {
            connector.stop();
        }
        if (app != null) {
            app.ctx.close();
        }
    }

    private Path keystore(String password) throws Exception {
        Path keytool = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").toLowerCase().startsWith("win") ? "keytool.exe" : "keytool");
        assumeTrue(Files.isExecutable(keytool), "keytool no disponible");
        Path ks = dir.resolve("receptor.p12");
        Process p = new ProcessBuilder(List.of(keytool.toString(), "-genkeypair", "-alias", "vivanet", "-keyalg", "EC",
                "-groupname", "secp256r1", "-validity", "30", "-dname", "CN=Vivanet Pay receptor local",
                "-ext", "SAN=ip:127.0.0.1", "-storetype", "PKCS12", "-keystore", ks.toString(),
                "-storepass", password, "-keypass", password)).redirectErrorStream(true).start();
        p.getInputStream().readAllBytes();
        assumeTrue(p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0, "keytool falló");
        return ks;
    }

    @Test
    void httpsConKeystorePkcs12() throws Exception {
        String pass = "clave-de-prueba-123";
        Path ks = keystore(pass);
        app = TestApp.create(dir);
        InMemorySecretStore secrets = new InMemorySecretStore();
        secrets.put("android.android-principal.tls_keystore_password", pass.toCharArray());
        connector = new AndroidWebhookConnector(new ConnectorDependencies("android-principal", app.ctx.ingestionService(),
                app.ctx.connectorRepository(), secrets, Map.of("port", "0", "tls.keystore", ks.toString()), dir, app.clock));
        connector.start();
        assertThat(connector.isRunning()).as(connector.state().detail()).isTrue();
        assertThat(connector.tlsEnabled()).isTrue();
        assertThat(connector.endpoints().get(0).getScheme()).isEqualTo("https");

        WebhookTestResult r = connector.sendTestEvent();
        assertThat(r.ok()).as(r.message()).isTrue();
        assertThat(app.ctx.transactionRepository().count()).isZero();

        // Un cliente que no confía en el certificado no puede conectar (no hay degradación a HTTP).
        HttpClient untrusting = HttpClient.newBuilder().proxy(HttpClient.Builder.NO_PROXY)
                .connectTimeout(Duration.ofSeconds(3)).build();
        boolean failed;
        try {
            untrusting.send(HttpRequest.newBuilder(connector.endpoints().get(0).resolve("/health")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            failed = false;
        } catch (java.io.IOException e) {
            failed = true;
        }
        assertThat(failed).isTrue();
    }

    @Test
    void contrasenaIncorrectaDejaElConectorEnError() throws Exception {
        Path ks = keystore("correcta-123456");
        app = TestApp.create(dir);
        InMemorySecretStore secrets = new InMemorySecretStore();
        secrets.put("android.android-principal.tls_keystore_password", "incorrecta".toCharArray());
        connector = new AndroidWebhookConnector(new ConnectorDependencies("android-principal", app.ctx.ingestionService(),
                app.ctx.connectorRepository(), secrets, Map.of("port", "0", "tls.keystore", ks.toString()), dir, app.clock));
        connector.start();
        assertThat(connector.isRunning()).isFalse();
        assertThat(connector.state().status()).isEqualTo(ConnectorStatus.ERROR);
        assertThat(connector.state().detail()).doesNotContain("incorrecta");
    }
}
