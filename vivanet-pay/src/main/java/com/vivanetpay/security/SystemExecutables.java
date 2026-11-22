package com.vivanetpay.security;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Rutas ABSOLUTAS de las herramientas de Windows que la aplicación lanza ({@code powershell.exe}, {@code reg.exe}).
 * Evita que un ejecutable homónimo en el directorio actual o en el PATH sea el que se ejecute (secuestro de binarios).
 * Si {@code %SystemRoot%} no existe (p. ej. no es Windows) devuelve el nombre simple como último recurso.
 */
public final class SystemExecutables {
    private SystemExecutables() {
    }

    public static String powerShell() {
        return resolve(Path.of("System32", "WindowsPowerShell", "v1.0", "powershell.exe"), "powershell.exe");
    }

    public static String regExe() {
        return resolve(Path.of("System32", "reg.exe"), "reg.exe");
    }

    private static String resolve(Path relativeToSystemRoot, String fallback) {
        String root = System.getenv("SystemRoot");
        if (root != null && !root.isBlank()) {
            Path p = Path.of(root).resolve(relativeToSystemRoot);
            if (Files.isRegularFile(p)) {
                return p.toString();
            }
        }
        return fallback;
    }
}
