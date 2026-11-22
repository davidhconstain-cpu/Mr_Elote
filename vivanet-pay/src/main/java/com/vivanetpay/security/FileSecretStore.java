package com.vivanetpay.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

/**
 * Almacén de secretos para sistemas NO Windows (desarrollo, pruebas, Linux/macOS): un archivo por clave con
 * permisos POSIX {@code 600} en un directorio {@code 700}.
 * <p>
 * <b>Advertencia:</b> los secretos NO se cifran; solo los protegen los permisos del sistema de archivos y la
 * cuenta del usuario. En producción (Windows) usar {@link WindowsSecretStore} vía {@link SecretStores#forCurrentOs}.
 */
public final class FileSecretStore extends AbstractFileSecretStore {
    private static final Logger log = LoggerFactory.getLogger(FileSecretStore.class);
    private static final Set<PosixFilePermission> FILE_PERMS = PosixFilePermissions.fromString("rw-------");
    private static final Set<PosixFilePermission> DIR_PERMS = PosixFilePermissions.fromString("rwx------");

    private final boolean posix;

    public FileSecretStore(Path dataDir) {
        super(dataDir, ".secret");
        this.posix = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
        log.warn("FileSecretStore: los secretos se guardan SIN cifrar en {} protegidos solo por permisos {}. "
                + "Uso recomendado solo fuera de Windows / desarrollo.", dir, posix ? "POSIX 600" : "(no POSIX: sin restricción)");
    }

    /** @return true si el sistema de archivos admite permisos POSIX (y por tanto se aplican 600/700). */
    public boolean posixPermissionsApplied() {
        return posix;
    }

    @Override
    protected void ensureDirectory() throws IOException {
        if (!Files.isDirectory(dir)) {
            if (posix) {
                Files.createDirectories(dir, PosixFilePermissions.asFileAttribute(DIR_PERMS));
            } else {
                Files.createDirectories(dir);
            }
        }
        if (posix) {
            Files.setPosixFilePermissions(dir, DIR_PERMS);
        }
    }

    @Override
    protected Path createTempFile() throws IOException {
        if (posix) {
            return Files.createTempFile(dir, "tmp-", ".part", PosixFilePermissions.asFileAttribute(FILE_PERMS));
        }
        return Files.createTempFile(dir, "tmp-", ".part");
    }

    @Override
    protected byte[] seal(byte[] plaintext, String key) {
        return plaintext.clone();
    }

    @Override
    protected byte[] open(byte[] stored, String key) {
        return stored.clone();
    }

    @Override
    public String toString() {
        return "FileSecretStore[" + dir + "]";
    }
}
