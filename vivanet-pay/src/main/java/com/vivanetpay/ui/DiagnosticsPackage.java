package com.vivanetpay.ui;

import com.vivanetpay.observability.Redactor;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Paquete de diagnóstico (IDD §10.3): ZIP con información del sistema y los logs, cada línea pasada por
 * {@link Redactor}. NO incluye la base de datos ni secretos. Debe crearse solo con consentimiento del usuario.
 */
public final class DiagnosticsPackage {
    private DiagnosticsPackage() {
    }

    public static Path create(Path logsDir, String summary, Path target) throws IOException {
        try (OutputStream out = Files.newOutputStream(target); ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("resumen.txt"));
            zip.write(Redactor.redact(summary).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            if (Files.isDirectory(logsDir)) {
                List<Path> files;
                try (Stream<Path> s = Files.list(logsDir)) {
                    files = s.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().startsWith("vivanetpay"))
                            .sorted().toList();
                }
                for (Path f : files) {
                    String name = f.getFileName().toString().replaceAll("\\.gz$", "");
                    zip.putNextEntry(new ZipEntry("logs/" + name));
                    try (InputStream in = openMaybeGz(f);
                         BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                         BufferedWriter w = new BufferedWriter(new OutputStreamWriter(zip, StandardCharsets.UTF_8) {
                             @Override
                             public void close() throws IOException {
                                 flush(); // no cerrar el ZIP
                             }
                         })) {
                        String line;
                        while ((line = r.readLine()) != null) {
                            w.write(Redactor.redact(line));
                            w.newLine();
                        }
                    }
                    zip.closeEntry();
                }
            }
        }
        return target;
    }

    private static InputStream openMaybeGz(Path f) throws IOException {
        InputStream in = Files.newInputStream(f);
        return f.getFileName().toString().endsWith(".gz") ? new GZIPInputStream(in) : in;
    }

    /** Última línea de nivel ERROR del log actual, redactada (para la pantalla Diagnóstico). */
    public static Optional<String> lastError(Path logsDir) {
        Path log = logsDir.resolve("vivanetpay.log");
        if (!Files.isRegularFile(log)) {
            return Optional.empty();
        }
        String last = null;
        try (BufferedReader r = Files.newBufferedReader(log, StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.contains(" ERROR ")) {
                    last = line;
                }
            }
        } catch (IOException e) {
            return Optional.empty();
        }
        return Optional.ofNullable(last).map(Redactor::redact);
    }
}
