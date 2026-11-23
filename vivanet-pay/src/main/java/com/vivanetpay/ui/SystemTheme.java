package com.vivanetpay.ui;

import com.vivanetpay.security.SystemExecutables;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Detecta si el sistema usa modo oscuro, sin depender de la versión de JavaFX (el proyecto compila contra la
 * línea LTS 21, que no tiene {@code Platform.Preferences}):
 * <ol>
 *   <li>Si en tiempo de ejecución existe {@code Platform.getPreferences()} (JavaFX 22+), se usa por reflexión y
 *       se sigue el cambio en vivo.</li>
 *   <li>Si no, en Windows se lee una vez {@code AppsUseLightTheme} con {@code reg query} (ruta absoluta,
 *       argumentos en lista, tiempo de espera corto).</li>
 *   <li>Cualquier fallo o plataforma distinta: tema claro.</li>
 * </ol>
 */
final class SystemTheme {
    private static final Logger log = LoggerFactory.getLogger(SystemTheme.class);
    static final String KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize";
    static final String VALUE = "AppsUseLightTheme";
    private static final long TIMEOUT_SECONDS = 3;

    private SystemTheme() {
    }

    static List<String> queryArgs() {
        return List.of(SystemExecutables.regExe(), "query", KEY, "/v", VALUE);
    }

    /**
     * Interpreta la salida de {@code reg query}: {@code AppsUseLightTheme REG_DWORD 0x0} = oscuro, {@code 0x1}
     * = claro. Cualquier otra cosa (vacío, error) = claro.
     */
    static boolean parseAppsUseLightTheme(String output) {
        if (output == null) {
            return false;
        }
        for (String line : output.split("\\R")) {
            String[] t = line.trim().split("\\s+");
            if (t.length >= 3 && VALUE.equalsIgnoreCase(t[0]) && "REG_DWORD".equalsIgnoreCase(t[1])) {
                String v = t[2].toLowerCase(Locale.ROOT);
                return v.equals("0x0") || v.equals("0");
            }
        }
        return false;
    }

    /** Lectura bloqueante del registro (llamar fuera del hilo de JavaFX). Falla => claro. */
    static boolean readWindowsDark() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
            return false;
        }
        Process p = null;
        try {
            p = new ProcessBuilder(queryArgs()).redirectErrorStream(true).start();
            Process proc = p;
            byte[][] out = new byte[1][];
            Thread drain = Thread.ofVirtual().start(() -> {
                try {
                    out[0] = proc.getInputStream().readAllBytes();
                } catch (IOException ignored) {
                    // el proceso terminó o se destruyó
                }
            });
            if (!p.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                return false;
            }
            drain.join(1_000);
            return p.exitValue() == 0 && out[0] != null
                    && parseAppsUseLightTheme(new String(out[0], StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.debug("No se pudo leer el tema de Windows: {}", e.getMessage());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            if (p != null && p.isAlive()) {
                p.destroyForcibly();
            }
        }
    }

    /**
     * Intenta engancharse a {@code Platform.getPreferences().colorSchemeProperty()} (JavaFX 22+) por reflexión.
     *
     * @return true si existe y quedó escuchando; {@code onChange} se invoca en el hilo de JavaFX con el valor
     *         inicial y con cada cambio
     */
    static boolean listenWithPlatformPreferences(Consumer<Boolean> onChange) {
        try {
            Object prefs = Platform.class.getMethod("getPreferences").invoke(null);
            @SuppressWarnings("unchecked")
            ObservableValue<Object> scheme = (ObservableValue<Object>) prefs.getClass()
                    .getMethod("colorSchemeProperty").invoke(prefs);
            ChangeListener<Object> l = (o, was, now) -> onChange.accept(isDarkValue(now));
            scheme.addListener(l);
            onChange.accept(isDarkValue(scheme.getValue()));
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false; // JavaFX < 22
        }
    }

    private static boolean isDarkValue(Object colorScheme) {
        return colorScheme != null && "DARK".equals(colorScheme.toString());
    }
}
