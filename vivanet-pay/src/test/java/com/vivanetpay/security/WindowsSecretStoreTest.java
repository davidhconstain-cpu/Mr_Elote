package com.vivanetpay.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WindowsSecretStoreTest {
    @TempDir
    Path dir;

    /** Protector falso (cualquier SO): XOR + verificación de entropía, para probar la lógica de archivos. */
    static final class FakeProtector implements DataProtector {
        @Override
        public byte[] protect(byte[] plaintext, byte[] entropy) {
            byte[] out = new byte[entropy.length + 1 + plaintext.length];
            System.arraycopy(entropy, 0, out, 0, entropy.length);
            out[entropy.length] = 0;
            for (int i = 0; i < plaintext.length; i++) {
                out[entropy.length + 1 + i] = (byte) (plaintext[i] ^ 0x5A);
            }
            return out;
        }

        @Override
        public byte[] unprotect(byte[] blob, byte[] entropy) throws IOException {
            if (blob.length <= entropy.length || !Arrays.equals(Arrays.copyOf(blob, entropy.length), entropy)) {
                throw new IOException("entropía incorrecta");
            }
            byte[] out = new byte[blob.length - entropy.length - 1];
            for (int i = 0; i < out.length; i++) {
                out[i] = (byte) (blob[entropy.length + 1 + i] ^ 0x5A);
            }
            return out;
        }
    }

    @Test
    void cifraConElProtectorYLigaElBlobASuClave() throws Exception {
        WindowsSecretStore store = new WindowsSecretStore(dir, new FakeProtector());
        store.put("android.a.webhook_secret", "SECRETO-PLANO".toCharArray());
        Path f = store.fileFor("android.a.webhook_secret");
        assertThat(f.getFileName().toString()).endsWith(".dpapi");
        assertThat(new String(Files.readAllBytes(f), StandardCharsets.ISO_8859_1)).doesNotContain("SECRETO-PLANO");
        assertThat(new String(store.get("android.a.webhook_secret").orElseThrow())).isEqualTo("SECRETO-PLANO");

        // Un blob copiado a otra clave no se descifra (entropía = clave).
        Files.copy(f, store.fileFor("android.b.webhook_secret"));
        assertThatThrownBy(() -> store.get("android.b.webhook_secret"))
                .isInstanceOf(SecretStoreException.class)
                .hasMessageNotContaining("SECRETO");
        store.delete("android.a.webhook_secret");
        assertThat(store.contains("android.a.webhook_secret")).isFalse();
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void dpapiRealIdaYVuelta() {
        WindowsSecretStore store = new WindowsSecretStore(dir);
        char[] secret = "ñandú-Ω-secreto-0123456789".toCharArray();
        store.put("test.dpapi.secret", secret);
        assertThat(store.get("test.dpapi.secret")).hasValueSatisfying(v -> assertThat(v).isEqualTo(secret));
        WindowsSecretStore other = new WindowsSecretStore(dir);
        assertThat(other.get("test.dpapi.secret")).isPresent();
        store.delete("test.dpapi.secret");
        assertThat(store.get("test.dpapi.secret")).isEmpty();
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void dpapiBlobManipuladoFalla() throws Exception {
        WindowsSecretStore store = new WindowsSecretStore(dir);
        store.put("test.dpapi.tamper", "abc".toCharArray());
        Path f = store.fileFor("test.dpapi.tamper");
        byte[] b = Files.readAllBytes(f);
        b[b.length / 2] ^= 0x01;
        Files.write(f, b);
        assertThatThrownBy(() -> store.get("test.dpapi.tamper")).isInstanceOf(SecretStoreException.class);
    }
}
