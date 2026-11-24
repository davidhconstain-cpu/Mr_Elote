package com.vivanetpay.integration.gmail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GmailCredentialsTest {

    static final String INSTALLED = "{\"installed\":{\"client_id\":\"123-abc.apps.googleusercontent.com\","
            + "\"client_secret\":\"GOCSPX-x\",\"auth_uri\":\"https://accounts.google.com/o/oauth2/auth\","
            + "\"token_uri\":\"https://oauth2.googleapis.com/token\",\"redirect_uris\":[\"http://localhost\"]}}";
    static final String WEB = INSTALLED.replace("\"installed\"", "\"web\"");

    @TempDir
    Path dir;

    private Path write(String name, String content) throws IOException {
        Path p = dir.resolve(name);
        Files.writeString(p, content, StandardCharsets.UTF_8);
        return p;
    }

    @Test
    void missingGivesFriendlyMessageWithExactPath() {
        Path file = dir.resolve("gmail").resolve("client_secret.json");
        assertThatThrownBy(() -> GmailCredentials.find(file)).isInstanceOf(GmailAuthException.class)
                .hasMessageContaining("Cargar archivo de credenciales")
                .hasMessageContaining(file.toAbsolutePath().toString())
                .hasMessageNotContaining("clientSecretsPath");
        assertThat(GmailCredentials.hasValid(file)).isFalse();
    }

    @Test
    void userFileWins() throws IOException {
        Path f = write("cs.json", INSTALLED);
        var found = GmailCredentials.find(f);
        assertThat(found.source()).isEqualTo(GmailCredentials.Source.USER_FILE);
        assertThat(found.secrets().getInstalled().getClientId()).isEqualTo("123-abc.apps.googleusercontent.com");
    }

    @Test
    void webTypeRejectedInSpanish() throws IOException {
        Path f = write("web.json", WEB);
        assertThatThrownBy(() -> GmailCredentials.find(f)).isInstanceOf(GmailAuthException.class)
                .hasMessageContaining("Aplicación web").hasMessageContaining("App de escritorio");
    }

    @Test
    void invalidContentsRejectedWithoutLeakingThem() throws IOException {
        assertThatThrownBy(() -> GmailCredentials.parse("no es json GOCSPX-leak".getBytes(StandardCharsets.UTF_8)))
                .hasMessageNotContaining("GOCSPX");
        assertThatThrownBy(() -> GmailCredentials.parse(INSTALLED.replace(".apps.googleusercontent.com", ".example.com")
                .getBytes(StandardCharsets.UTF_8))).hasMessageContaining("client_id");
        assertThatThrownBy(() -> GmailCredentials.parse("{\"otro\":{}}".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(GmailAuthException.class);
    }

    @Test
    void tooLargeRejected() throws IOException {
        Path f = write("big.json", INSTALLED + " ".repeat(GmailCredentials.MAX_BYTES));
        assertThatThrownBy(() -> GmailCredentials.install(f, dir.resolve("out").resolve("client_secret.json")))
                .hasMessageContaining("demasiado grande");
    }

    @Test
    void installCopiesAtomicallyCreatesFolderAndSetsPosix600() throws IOException {
        Path src = write("descarga.json", INSTALLED);
        Path target = dir.resolve("datos").resolve("gmail").resolve("client_secret.json");
        GmailCredentials.install(src, target);
        assertThat(Files.readString(target)).isEqualTo(INSTALLED);
        assertThat(GmailCredentials.hasValid(target)).isTrue();
        try (var list = Files.list(target.getParent())) {
            assertThat(list.map(p -> p.getFileName().toString())).containsExactly("client_secret.json");
        }
        if (target.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            assertThat(Files.getPosixFilePermissions(target))
                    .isEqualTo(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        }
        // reemplazar
        GmailCredentials.install(src, target);
    }

    @Test
    void installRejectsInvalidAndLeavesExistingUntouched() throws IOException {
        Path target = dir.resolve("gmail").resolve("client_secret.json");
        GmailCredentials.install(write("ok.json", INSTALLED), target);
        Path bad = write("web.json", WEB);
        assertThatThrownBy(() -> GmailCredentials.install(bad, target)).hasMessageContaining("Aplicación web");
        assertThat(Files.readString(target)).isEqualTo(INSTALLED);
    }

    @Test
    void bundledResourceUsedWhenNoUserFile() throws Exception {
        // el recurso se ve desde el classloader de pruebas: se coloca en el directorio de clases de pruebas
        var url = GmailCredentials.class.getProtectionDomain().getCodeSource().getLocation();
        Path classes = Path.of(url.toURI()).resolveSibling("test-classes");
        Path bundled = classes.resolve("vivanetpay/gmail/client_secret.json");
        assertThat(GmailCredentials.class.getResource(GmailCredentials.BUNDLED_RESOURCE)).isNull();
        Files.createDirectories(bundled.getParent());
        Files.writeString(bundled, INSTALLED);
        try {
            Path none = dir.resolve("nope.json");
            assertThat(GmailCredentials.find(none).source()).isEqualTo(GmailCredentials.Source.BUNDLED);
            // el archivo del usuario gana
            Path user = write("user.json", INSTALLED.replace("123-abc", "999-user"));
            var f = GmailCredentials.find(user);
            assertThat(f.source()).isEqualTo(GmailCredentials.Source.USER_FILE);
            assertThat(f.secrets().getInstalled().getClientId()).startsWith("999-user");
        } finally {
            Files.deleteIfExists(bundled);
        }
    }

    @Test
    void defaultFileUnderDataDir() {
        assertThat(GmailCredentials.defaultFile(Path.of("d"))).isEqualTo(Path.of("d/gmail/client_secret.json"));
    }
}
