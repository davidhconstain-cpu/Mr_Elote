package com.vivanetpay.app;

import com.vivanetpay.domain.ConnectorType;
import com.vivanetpay.domain.EventSource;
import com.vivanetpay.validation.ValidationPolicyConfig;
import com.vivanetpay.voice.VoiceSettings;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Configuración NO secreta de la aplicación (IDD §12.2: datos separados del directorio de instalación).
 * <p>
 * Directorio de datos: propiedad de sistema {@value #DATA_DIR_PROPERTY}; si no, {@code %APPDATA%\VivanetPay} en
 * Windows; si no, {@code ~/.vivanetpay}. Lee {@code <dataDir>/config.properties} si existe. Los secretos van en
 * {@code SecretStore}, nunca aquí.
 * <p>
 * Claves y valores por defecto (los marcados DP-xx son PROVISIONALES):
 * <pre>
 * connectors.enabled=gmail,android        (lista; "none" = ninguno)
 * connector.&lt;tipo&gt;.&lt;clave&gt;=...             (ajustes no secretos del conector)
 * policy.automaticValidation=true          (DP-01 decidida; false = modo conservador)
 * policy.trustedSources=ANDROID_NEQUI,GMAIL (DP-01; vacío explícito = ninguna fuente)
 * policy.minConfidence=0.9
 * policy.announceUnconfirmed=true          (anunciar "posible pago")
 * policy.announceOnManualValidation=false
 * idempotency.similarityWindowMinutes=10
 * voice.enabled=true  voice.volume=80  voice.rate=0  voice.name=
 * voice.includeSender=false                (DP-09)
 * voice.maxAgeMinutes=15                   (no anunciar históricos)
 * voice.connectorAlerts=true
 * export.csv.separator=;                 (DP-08; ; , | tab)
 * backup.onStart=true                      (frecuencia/retención DP-05/DP-10)
 * license.enforce=false                    (DP-07)
 * </pre>
 */
public final class AppConfig {
    public static final String DATA_DIR_PROPERTY = "vivanet.data.dir";
    public static final String LOG_DIR_PROPERTY = "vivanet.log.dir";
    public static final String CONFIG_FILE = "config.properties";
    public static final String APP_FOLDER_WINDOWS = "VivanetPay";
    public static final String APP_FOLDER_UNIX = ".vivanetpay";

    private final Path dataDir;
    private final Properties props;

    private AppConfig(Path dataDir, Properties props) {
        this.dataDir = dataDir.toAbsolutePath();
        this.props = props;
    }

    /** Carga la configuración del directorio de datos por defecto. */
    public static AppConfig load() {
        return load(defaultDataDir());
    }

    /** Carga {@code config.properties} del directorio dado (si existe). */
    public static AppConfig load(Path dataDir) {
        Properties p = new Properties();
        Path file = dataDir.resolve(CONFIG_FILE);
        if (Files.isRegularFile(file)) {
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                p.load(r);
            } catch (IOException e) {
                throw new IllegalStateException("No se pudo leer " + file, e);
            }
        }
        return new AppConfig(dataDir, p);
    }

    /** Configuración en memoria (pruebas). */
    public static AppConfig of(Path dataDir, Map<String, String> values) {
        Properties p = new Properties();
        p.putAll(values);
        return new AppConfig(dataDir, p);
    }


    /** Directorio de datos por defecto según el sistema operativo. */
    public static Path defaultDataDir() {
        String override = System.getProperty(DATA_DIR_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String appData = System.getenv("APPDATA");
        if (os.contains("win") && appData != null && !appData.isBlank()) {
            return Path.of(appData, APP_FOLDER_WINDOWS);
        }
        return Path.of(System.getProperty("user.home"), APP_FOLDER_UNIX);
    }

    public Path dataDir() {
        return dataDir;
    }

    public Path databaseFile() {
        return dataDir.resolve("vivanetpay.db");
    }

    public Path logsDir() {
        return dataDir.resolve("logs");
    }

    public Path backupsDir() {
        return dataDir.resolve("backups");
    }

    /** Crea los directorios de datos, logs y respaldos. */
    public void ensureDirectories() {
        try {
            Files.createDirectories(dataDir);
            Files.createDirectories(logsDir());
            Files.createDirectories(backupsDir());
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo crear el directorio de datos " + dataDir, e);
        }
    }

    /**
     * Fija valores en memoria y los persiste en {@code config.properties} de forma atómica: conserva comentarios y
     * claves ajenas, escribe un archivo temporal en el mismo directorio, lo sincroniza a disco y lo mueve sobre el
     * original (ATOMIC_MOVE). Un valor {@code null} elimina la clave. Nunca guardar secretos aquí.
     */
    public synchronized void update(Map<String, String> changes) {
        Path file = dataDir.resolve(CONFIG_FILE);
        try {
            Files.createDirectories(dataDir);
            List<String> lines = Files.isRegularFile(file) ? Files.readAllLines(file, StandardCharsets.UTF_8) : List.of();
            List<String> out = new ArrayList<>();
            Set<String> written = new java.util.HashSet<>();
            for (String line : lines) {
                String key = propertyKey(line);
                if (key != null && changes.containsKey(key)) {
                    String v = changes.get(key);
                    if (v != null && written.add(key)) {
                        out.add(escapeKey(key) + "=" + escapeValue(v));
                    }
                    continue; // línea reemplazada o eliminada (también duplicados)
                }
                out.add(line);
            }
            for (Map.Entry<String, String> e : changes.entrySet()) {
                if (e.getValue() != null && !written.contains(e.getKey())) {
                    out.add(escapeKey(e.getKey()) + "=" + escapeValue(e.getValue()));
                }
            }
            Path tmp = Files.createTempFile(dataDir, "config", ".tmp");
            try {
                Files.write(tmp, out, StandardCharsets.UTF_8);
                try (java.nio.channels.FileChannel ch = java.nio.channels.FileChannel.open(tmp,
                        java.nio.file.StandardOpenOption.WRITE)) {
                    ch.force(true);
                }
                try {
                    Files.move(tmp, file, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                    Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo guardar " + file, e);
        }
        for (Map.Entry<String, String> e : changes.entrySet()) { // solo tras persistir con éxito
            if (e.getValue() == null) {
                props.remove(e.getKey());
            } else {
                props.setProperty(e.getKey(), e.getValue());
            }
        }
    }

    public void set(String key, String value) {
        java.util.HashMap<String, String> m = new java.util.HashMap<>();
        m.put(key, value);
        update(m);
    }

    private static String propertyKey(String line) {
        String t = line.stripLeading();
        if (t.isEmpty() || t.charAt(0) == '#' || t.charAt(0) == '!') {
            return null;
        }
        int i = 0;
        StringBuilder k = new StringBuilder();
        while (i < t.length()) {
            char ch = t.charAt(i);
            if (ch == '\\' && i + 1 < t.length()) {
                k.append(t.charAt(i + 1));
                i += 2;
                continue;
            }
            if (ch == '=' || ch == ':' || Character.isWhitespace(ch)) {
                break;
            }
            k.append(ch);
            i++;
        }
        return k.toString();
    }

    private static String escapeKey(String s) {
        return s.replace("\\", "\\\\").replace(" ", "\\ ").replace("=", "\\=").replace(":", "\\:");
    }

    private static String escapeValue(String s) {
        return s.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r");
    }

    public String get(String key, String def) {
        String v = props.getProperty(key);
        return v == null || v.isBlank() ? def : v.trim();
    }

    public boolean getBoolean(String key, boolean def) {
        String v = props.getProperty(key);
        return v == null || v.isBlank() ? def : Boolean.parseBoolean(v.trim());
    }

    public int getInt(String key, int def) {
        String v = props.getProperty(key);
        return v == null || v.isBlank() ? def : Integer.parseInt(v.trim());
    }

    public double getDouble(String key, double def) {
        String v = props.getProperty(key);
        return v == null || v.isBlank() ? def : Double.parseDouble(v.trim());
    }

    /** Política de validación. Defaults de la decisión DP-01 (confirmación automática de fuentes verificadas). */
    public ValidationPolicyConfig policyConfig() {
        ValidationPolicyConfig d = ValidationPolicyConfig.defaults();
        Set<EventSource> trusted = EnumSet.noneOf(EventSource.class);
        String trustedRaw = props.getProperty("policy.trustedSources");
        if (trustedRaw == null) {
            trusted.addAll(d.trustedSources());
            trustedRaw = "";
        }
        for (String s : trustedRaw.split(",")) {
            if (!s.isBlank()) {
                trusted.add(EventSource.valueOf(s.trim().toUpperCase(Locale.ROOT)));
            }
        }
        return new ValidationPolicyConfig(
                getBoolean("policy.automaticValidation", d.automaticValidationEnabled()),
                trusted,
                getDouble("policy.minConfidence", d.minConfidence()),
                getBoolean("policy.announceUnconfirmed", d.announceUnconfirmed()),
                getBoolean("policy.announceOnManualValidation", d.announceOnManualValidation()));
    }

    public VoiceSettings voiceSettings() {
        VoiceSettings d = VoiceSettings.defaults();
        String name = get("voice.name", null);
        return new VoiceSettings(getBoolean("voice.enabled", d.enabled()), getInt("voice.volume", d.volume()),
                getInt("voice.rate", d.rate()), name, getBoolean("voice.includeSender", d.includeSender()));
    }

    public Duration announcementMaxAge() {
        return Duration.ofMinutes(getInt("voice.maxAgeMinutes", 15));
    }

    public boolean connectorVoiceAlerts() {
        return getBoolean("voice.connectorAlerts", true);
    }

    public Duration similarityWindow() {
        return Duration.ofMinutes(getInt("idempotency.similarityWindowMinutes", 10));
    }

    /** Separador del CSV exportado ({@code export.csv.separator}; por defecto {@code ;}, DP-08). */
    public char csvSeparator() {
        return com.vivanetpay.reporting.CsvExporter.parseSeparator(get("export.csv.separator", ";"));
    }

    public boolean backupOnStart() {
        return getBoolean("backup.onStart", true);
    }

    public boolean licenseEnforce() {
        return getBoolean("license.enforce", false);
    }

    /** Por defecto arrancan ambos: Gmail sin credenciales queda REQUIERE_AUTORIZACION (sin red) y Android escucha en 127.0.0.1. */
    public static final String DEFAULT_CONNECTORS = "gmail,android";

    /** Conectores habilitados ({@code connectors.enabled=gmail,android}; {@code none} no arranca ninguno). */
    public List<ConnectorType> enabledConnectors() {
        List<ConnectorType> out = new ArrayList<>();
        for (String s : get("connectors.enabled", DEFAULT_CONNECTORS).split(",")) {
            if (!s.isBlank() && !s.trim().equalsIgnoreCase("none")) {
                out.add(ConnectorType.fromConfigKey(s));
            }
        }
        return out;
    }

    /** Ajustes no secretos de un conector: claves {@code connector.<tipo>.x} devueltas como {@code x}. */
    public Map<String, String> connectorSettings(ConnectorType type) {
        String prefix = "connector." + type.configKey() + ".";
        Map<String, String> out = new LinkedHashMap<>();
        for (String k : props.stringPropertyNames()) {
            if (k.startsWith(prefix)) {
                out.put(k.substring(prefix.length()), props.getProperty(k).trim());
            }
        }
        return out;
    }
}
