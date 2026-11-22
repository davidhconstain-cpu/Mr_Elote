package com.vivanetpay.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vivanetpay.security.SystemExecutables;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Inicio con Windows (CP-002): valor en {@code HKCU\Software\Microsoft\Windows\CurrentVersion\Run} manejado con
 * {@code reg.exe} (argumentos en lista, sin shell). El valor apunta al ejecutable empaquetado con {@code --minimized}.
 * Fuera de Windows es un no-op informativo.
 */
public final class WindowsStartup {
    private static final Logger log = LoggerFactory.getLogger(WindowsStartup.class);
    public static final String RUN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    public static final String VALUE_NAME = "VivanetPay";
    public static final String MINIMIZED_ARG = "--minimized";

    /** Resultado de la operación. */
    public record Result(boolean ok, String message) {
    }

    private final boolean windows;

    public WindowsStartup() {
        this(System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win"));
    }

    WindowsStartup(boolean windows) {
        this.windows = windows;
    }

    public boolean supported() {
        return windows;
    }

    /** Valor que se escribe en el registro: ruta entre comillas + argumento. */
    public static String commandLine(String executablePath) {
        if (executablePath == null || executablePath.isBlank() || executablePath.contains("\"")) {
            throw new IllegalArgumentException("Ruta de ejecutable inválida");
        }
        return "\"" + executablePath + "\" " + MINIMIZED_ARG;
    }

    /**
     * Argumentos de {@code reg add}. {@code reg.exe} se invoca por ruta absoluta (no por PATH/directorio actual).
     * Java NO escapa las comillas internas de un argumento al armar la línea de comandos de Windows, así que el dato
     * {@code /d} lleva las comillas escapadas como {@code \"} (convención de reg.exe/CRT): en el registro queda
     * {@code "C:\ruta con espacios\VivanetPay.exe" --minimized}.
     */
    public static List<String> addArgs(String executablePath) {
        return List.of(SystemExecutables.regExe(), "add", RUN_KEY, "/v", VALUE_NAME, "/t", "REG_SZ", "/d",
                commandLine(executablePath).replace("\"", "\\\""), "/f");
    }

    public static List<String> deleteArgs() {
        return List.of(SystemExecutables.regExe(), "delete", RUN_KEY, "/v", VALUE_NAME, "/f");
    }

    public static List<String> queryArgs() {
        return List.of(SystemExecutables.regExe(), "query", RUN_KEY, "/v", VALUE_NAME);
    }

    /**
     * Ruta del ejecutable empaquetado (jpackage): propiedad {@code jpackage.app-path} que fija el launcher, o el
     * proceso actual si es un .exe. Null si no se está ejecutando desde un paquete (desarrollo).
     */
    public static String detectExecutable() {
        String p = System.getProperty("jpackage.app-path");
        if (p != null && !p.isBlank()) {
            return p;
        }
        return ProcessHandle.current().info().command()
                .filter(c -> c.toLowerCase(Locale.ROOT).endsWith("vivanetpay.exe")).orElse(null);
    }

    public Result enable(String executablePath) {
        if (!windows) {
            return new Result(false, "El inicio con Windows solo está disponible en Windows.");
        }
        if (executablePath == null) {
            return new Result(false, "Solo se puede activar desde la aplicación instalada (no desde el entorno de desarrollo).");
        }
        return run(addArgs(executablePath), "Inicio con Windows activado.");
    }

    public Result disable() {
        if (!windows) {
            return new Result(false, "El inicio con Windows solo está disponible en Windows.");
        }
        Result r = run(deleteArgs(), "Inicio con Windows desactivado.");
        // reg delete devuelve error si el valor no existía: ya está desactivado
        return r.ok() || !isEnabled() ? new Result(true, "Inicio con Windows desactivado.") : r;
    }

    public boolean isEnabled() {
        if (!windows) {
            return false;
        }
        try {
            return exec(queryArgs()) == 0;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    private Result run(List<String> args, String okMessage) {
        try {
            int code = exec(args);
            if (code == 0) {
                return new Result(true, okMessage);
            }
            log.warn("reg.exe terminó con código {}", code);
            return new Result(false, "Windows rechazó el cambio (código " + code + ").");
        } catch (IOException e) {
            log.warn("No se pudo ejecutar reg.exe: {}", e.getMessage());
            return new Result(false, "No se pudo ejecutar reg.exe.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(false, "Operación interrumpida.");
        }
    }

    private static int exec(List<String> args) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(args).redirectErrorStream(true).start();
        try {
            // La salida se vacía en un hilo aparte para que el tiempo de espera sea real aunque reg.exe se cuelgue.
            Thread drain = Thread.ofVirtual().start(() -> {
                try {
                    p.getInputStream().readAllBytes();
                } catch (IOException ignored) {
                    // el proceso terminó o se destruyó
                }
            });
            if (!p.waitFor(15, TimeUnit.SECONDS)) {
                throw new IOException("reg.exe no respondió");
            }
            drain.join(2_000);
            return p.exitValue();
        } finally {
            if (p.isAlive()) {
                p.destroyForcibly();
            }
        }
    }

}
