package com.vivanetpay.security;

import java.nio.file.Path;
import java.util.Locale;

/** Fábrica del almacén de secretos adecuado al sistema operativo. */
public final class SecretStores {
    private SecretStores() {
    }

    /**
     * @param dataDir directorio de datos de la aplicación (los secretos van en {@code <dataDir>/secrets})
     * @return {@link WindowsSecretStore} (DPAPI) en Windows; {@link FileSecretStore} (archivos 600, sin cifrar,
     *         con advertencia en log) en otros sistemas.
     */
    public static SecretStore forCurrentOs(Path dataDir) {
        return isWindows() ? new WindowsSecretStore(dataDir) : new FileSecretStore(dataDir);
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }
}
