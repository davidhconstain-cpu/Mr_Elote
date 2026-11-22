package com.vivanetpay.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

/**
 * Base de los almacenes de secretos en archivos: un archivo por clave en {@code <dataDir>/secrets/}, con nombre
 * derivado de SHA-256 de la clave (no revela la clave ni permite rutas maliciosas), escritura atómica
 * (temporal + move) y transformación ({@link #seal}/{@link #open}) definida por la subclase.
 */
abstract class AbstractFileSecretStore implements SecretStore {
    static final String SECRETS_DIR = "secrets";

    protected final Path dir;
    private final String extension;

    protected AbstractFileSecretStore(Path dataDir, String extension) {
        this.dir = Objects.requireNonNull(dataDir, "dataDir").resolve(SECRETS_DIR);
        this.extension = extension;
    }

    /** Crea el directorio de secretos (con permisos restringidos cuando el SO lo permita). */
    protected abstract void ensureDirectory() throws IOException;

    /** Crea el archivo temporal vacío con permisos restringidos. */
    protected abstract Path createTempFile() throws IOException;

    /** Transforma el secreto en claro (UTF-8) en el contenido a guardar. */
    protected abstract byte[] seal(byte[] plaintext, String key) throws IOException;

    /** Inverso de {@link #seal}. */
    protected abstract byte[] open(byte[] stored, String key) throws IOException;

    @Override
    public synchronized Optional<char[]> get(String key) {
        Path file = fileFor(key);
        byte[] stored = null;
        byte[] plain = null;
        try {
            stored = Files.readAllBytes(file);
            plain = open(stored, key);
            return Optional.of(SecretBytes.fromUtf8(plain));
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new SecretStoreException("No se pudo leer el secreto '" + key + "'", e);
        } finally {
            SecretBytes.wipe(stored);
            SecretBytes.wipe(plain);
        }
    }

    @Override
    public synchronized void put(String key, char[] secret) {
        Objects.requireNonNull(secret, "secret");
        Path file = fileFor(key);
        byte[] plain = SecretBytes.toUtf8(secret);
        byte[] sealed = null;
        Path tmp = null;
        try {
            ensureDirectory();
            sealed = seal(plain, key);
            tmp = createTempFile();
            Files.write(tmp, sealed);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            tmp = null;
        } catch (IOException e) {
            throw new SecretStoreException("No se pudo guardar el secreto '" + key + "'", e);
        } finally {
            SecretBytes.wipe(plain);
            SecretBytes.wipe(sealed);
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                    // se limpiará en el próximo arranque
                }
            }
        }
    }

    @Override
    public synchronized void delete(String key) {
        try {
            Files.deleteIfExists(fileFor(key));
        } catch (IOException e) {
            throw new SecretStoreException("No se pudo eliminar el secreto '" + key + "'", e);
        }
    }

    @Override
    public synchronized boolean contains(String key) {
        return Files.isRegularFile(fileFor(key));
    }

    /** Ruta del archivo de una clave (visible para pruebas). */
    Path fileFor(String key) {
        Objects.requireNonNull(key, "key");
        if (key.isBlank()) {
            throw new IllegalArgumentException("La clave no puede estar vacía");
        }
        return dir.resolve("s-" + sha256Hex(key) + extension);
    }

    static byte[] entropyFor(String key) {
        return ("VivanetPay.SecretStore.v1|" + key).getBytes(StandardCharsets.UTF_8);
    }

    private static String sha256Hex(String key) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(key.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
