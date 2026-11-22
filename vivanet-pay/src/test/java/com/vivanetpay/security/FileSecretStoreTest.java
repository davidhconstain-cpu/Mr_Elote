package com.vivanetpay.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.assertj.core.api.Assertions.assertThat;

class FileSecretStoreTest {
    @TempDir
    Path dir;

    @Test
    void guardaLeeReemplazaYElimina() {
        FileSecretStore store = new FileSecretStore(dir);
        assertThat(store.get("android.x.webhook_secret")).isEmpty();
        assertThat(store.contains("android.x.webhook_secret")).isFalse();
        store.put("android.x.webhook_secret", "sécreto-1".toCharArray());
        assertThat(store.get("android.x.webhook_secret")).hasValueSatisfying(v -> assertThat(new String(v)).isEqualTo("sécreto-1"));
        store.put("android.x.webhook_secret", "otro".toCharArray());
        assertThat(new String(store.get("android.x.webhook_secret").orElseThrow())).isEqualTo("otro");
        assertThat(new FileSecretStore(dir).get("android.x.webhook_secret")).isPresent(); // persiste
        store.delete("android.x.webhook_secret");
        store.delete("android.x.webhook_secret"); // idempotente
        assertThat(store.get("android.x.webhook_secret")).isEmpty();
    }

    @Test
    void nombreDeArchivoNoRevelaLaClaveNiPermiteRutas() {
        FileSecretStore store = new FileSecretStore(dir);
        store.put("../../etc/passwd", "x".toCharArray());
        Path f = store.fileFor("../../etc/passwd");
        assertThat(f.getParent()).isEqualTo(dir.resolve("secrets"));
        assertThat(f.getFileName().toString()).matches("s-[0-9a-f]{64}\\.secret");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void permisosPosix600y700() throws Exception {
        FileSecretStore store = new FileSecretStore(dir);
        assertThat(store.posixPermissionsApplied()).isTrue();
        store.put("gmail.g.refresh_token", "tok".toCharArray());
        Path f = store.fileFor("gmail.g.refresh_token");
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(f))).isEqualTo("rw-------");
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(dir.resolve("secrets")))).isEqualTo("rwx------");
        try (var files = Files.list(dir.resolve("secrets"))) {
            assertThat(files.filter(p -> p.toString().endsWith(".part")).count()).as("sin temporales").isZero();
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void fabricaFueraDeWindowsUsaFileSecretStore() {
        assertThat(SecretStores.forCurrentOs(dir)).isInstanceOf(FileSecretStore.class);
        assertThat(SecretStores.isWindows()).isFalse();
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void fabricaEnWindowsUsaDpapi() {
        assertThat(SecretStores.forCurrentOs(dir)).isInstanceOf(WindowsSecretStore.class);
    }
}
