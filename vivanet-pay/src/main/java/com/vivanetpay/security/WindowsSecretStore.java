package com.vivanetpay.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Almacén de secretos para Windows (RNF-002, IDD §8.3): cada secreto se cifra con DPAPI en ámbito
 * {@code CurrentUser} y el blob se guarda en {@code <dataDir>/secrets/s-<sha256(clave)>.dpapi}. Solo el mismo
 * usuario de Windows en el mismo equipo puede descifrarlo. La clave del secreto se usa como entropía adicional,
 * de modo que un blob copiado a otra clave no se descifra.
 * <p>
 * DPAPI se invoca con {@link PowerShellDpapiProtector} ({@code powershell -NoProfile -NonInteractive}); los datos
 * viajan por stdin, nunca por la línea de comandos. Cada operación lanza un proceso (cientos de ms): los
 * consumidores deben cachear en memoria lo que lean con frecuencia.
 */
public final class WindowsSecretStore extends AbstractFileSecretStore {
    private final DataProtector protector;

    public WindowsSecretStore(Path dataDir) {
        this(dataDir, new PowerShellDpapiProtector());
    }

    /** Constructor con protector inyectable (pruebas). */
    public WindowsSecretStore(Path dataDir, DataProtector protector) {
        super(dataDir, ".dpapi");
        this.protector = Objects.requireNonNull(protector, "protector");
    }

    @Override
    protected void ensureDirectory() throws IOException {
        Files.createDirectories(dir);
    }

    @Override
    protected Path createTempFile() throws IOException {
        return Files.createTempFile(dir, "tmp-", ".part");
    }

    @Override
    protected byte[] seal(byte[] plaintext, String key) throws IOException {
        return protector.protect(plaintext, entropyFor(key));
    }

    @Override
    protected byte[] open(byte[] stored, String key) throws IOException {
        return protector.unprotect(stored, entropyFor(key));
    }

    @Override
    public String toString() {
        return "WindowsSecretStore[DPAPI CurrentUser, " + dir + "]";
    }
}
