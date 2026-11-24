package com.vivanetpay.integration.gmail;

import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Optional;

/**
 * Localiza y valida el archivo OAuth de Google ("App de escritorio"). Orden de búsqueda: (a) el archivo del usuario
 * ({@code gmail.clientSecretsPath}, por defecto {@code <datos>/gmail/client_secret.json}); (b) el recurso empaquetado
 * {@value #BUNDLED_RESOURCE} que el instalador puede traer; (c) ninguno. El contenido nunca se registra.
 */
public final class GmailCredentials {

    /** Recurso opcional dentro del JAR (lo escribe el CI desde el secreto del proveedor; no está en el repositorio). */
    public static final String BUNDLED_RESOURCE = "/vivanetpay/gmail/client_secret.json";
    /** Tamaño máximo razonable de un client_secret.json (el real pesa ~400 bytes). */
    public static final int MAX_BYTES = 64 * 1024;
    private static final String CLIENT_ID_SUFFIX = ".apps.googleusercontent.com";
    private static final JsonFactory JSON = GsonFactory.getDefaultInstance();

    private GmailCredentials() {
    }

    /** Origen de las credenciales encontradas. */
    public enum Source { USER_FILE, BUNDLED }

    /** Credenciales ya validadas. */
    public record Found(Source source, GoogleClientSecrets secrets) {
    }

    /** Carpeta/archivo por defecto donde la app busca y donde se copia el archivo cargado. */
    public static Path defaultFile(Path dataDir) {
        return dataDir.resolve("gmail").resolve("client_secret.json");
    }

    /** Mensaje para usuario no técnico cuando no hay credenciales, con la ruta exacta de este equipo. */
    public static String missingMessage(Path userFile) {
        return "Para conectar Gmail, Vivanet Pay necesita el archivo de credenciales de Google. "
                + "Pulsa «Cargar archivo de credenciales…» y elige el archivo client_secret….json que descargaste de "
                + "Google Cloud, o cópialo en: " + (userFile == null ? "(carpeta de datos de Vivanet Pay)" : userFile.toAbsolutePath());
    }

    /** @return true si hay un archivo del usuario o uno empaquetado (sin validar su contenido). */
    public static boolean available(Path userFile) {
        return (userFile != null && Files.isRegularFile(userFile)) || GmailCredentials.class.getResource(BUNDLED_RESOURCE) != null;
    }

    /** @return true si hay un archivo del usuario o uno empaquetado válido. */
    public static boolean hasValid(Path userFile) {
        try {
            find(userFile);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Aplica el orden de búsqueda y valida. {@link GmailAuthException} (mensaje amigable) si falta o es inválido. */
    public static Found find(Path userFile) throws IOException {
        if (userFile != null && Files.isRegularFile(userFile)) {
            return new Found(Source.USER_FILE, parse(read(Files.newInputStream(userFile))));
        }
        try (InputStream in = GmailCredentials.class.getResourceAsStream(BUNDLED_RESOURCE)) {
            if (in != null) {
                return new Found(Source.BUNDLED, parse(read(in)));
            }
        }
        throw new GmailAuthException(missingMessage(userFile));
    }

    private static byte[] read(InputStream in) throws IOException {
        try (in) {
            byte[] data = in.readNBytes(MAX_BYTES + 1);
            if (data.length > MAX_BYTES) {
                throw new GmailAuthException("Ese archivo es demasiado grande para ser un archivo de credenciales de Google.");
            }
            return data;
        }
    }

    /** Valida el contenido (tipo "installed", client_id de Google). No registra nada del contenido. */
    public static GoogleClientSecrets parse(byte[] data) throws GmailAuthException {
        GoogleClientSecrets secrets;
        try {
            secrets = GoogleClientSecrets.load(JSON, new StringReader(new String(data, StandardCharsets.UTF_8)));
        } catch (IOException | RuntimeException e) {
            throw invalid();
        }
        if (secrets.getInstalled() == null) {
            if (secrets.getWeb() != null) {
                throw new GmailAuthException("Ese archivo es de tipo Aplicación web; crea uno de tipo App de escritorio "
                        + "en Google Cloud (Credenciales > Crear credenciales > ID de cliente de OAuth > App de escritorio).");
            }
            throw new GmailAuthException("Ese archivo no es de tipo App de escritorio (\"installed\"); "
                    + "crea un ID de cliente de OAuth de tipo App de escritorio en Google Cloud y descarga su JSON.");
        }
        String id = secrets.getInstalled().getClientId();
        if (id == null || !id.endsWith(CLIENT_ID_SUFFIX)) {
            throw new GmailAuthException("El archivo no tiene un client_id de Google válido (debe terminar en "
                    + CLIENT_ID_SUFFIX + "). Descárgalo de nuevo desde Google Cloud.");
        }
        return secrets;
    }

    private static GmailAuthException invalid() {
        return new GmailAuthException("El archivo de credenciales de Google no es válido. "
                + "Descárgalo de nuevo desde Google Cloud (cliente 'App de escritorio').");
    }

    /**
     * Valida {@code source} y lo copia de forma atómica a {@code target} (crea la carpeta; permisos 600 en POSIX).
     * Bloqueante: llamar fuera del hilo de interfaz.
     */
    public static void install(Path source, Path target) throws IOException {
        if (!Files.isRegularFile(source)) {
            throw new GmailAuthException("No se pudo leer el archivo elegido.");
        }
        byte[] data;
        try {
            data = read(Files.newInputStream(source));
        } catch (GmailAuthException e) {
            throw e;
        } catch (IOException e) {
            throw new GmailAuthException("No se pudo leer el archivo elegido.");
        }
        parse(data);
        Path dir = target.toAbsolutePath().getParent();
        Files.createDirectories(dir);
        Path tmp = Files.createTempFile(dir, "client_secret", ".tmp");
        try {
            Files.write(tmp, data);
            try {
                Files.setPosixFilePermissions(tmp, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) {
                // Windows: no hace falta
            }
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /** Para pruebas/UI: texto de origen sin exponer contenido. */
    public static Optional<Source> sourceOf(Path userFile) {
        try {
            return Optional.of(find(userFile).source());
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
