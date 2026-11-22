package com.vivanetpay.reporting;

import com.vivanetpay.domain.AppTime;
import com.vivanetpay.domain.EventStatus;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Exportación CSV del historial filtrado (RF-020).
 * <p>
 * NOTA DP-08 (formato de exportación pendiente): solo se implementa CSV. Excel/PDF quedan para cuando se cierre
 * la decisión. El archivo es UTF-8 con BOM (Excel en Windows lo reconoce y respeta las tildes), separador coma,
 * fin de línea CRLF, importes en pesos enteros sin separadores y horas en America/Bogota.
 * <p>
 * Protección contra inyección de fórmulas (CSV injection): todo texto libre que empiece por {@code = + - @},
 * tabulación, retorno de carro o salto de línea se antepone con una comilla simple.
 */
public final class CsvExporter {
    public static final char BOM = '﻿';
    public static final String EOL = "\r\n";
    static final List<String> HEADER = List.of("ID evento", "Fecha y hora (America/Bogota)", "Fuente", "Conector",
            "Entidad", "Monto (COP)", "Remitente", "Referencia", "Estado", "Motivo del estado", "Recibido (America/Bogota)");

    private CsvExporter() {
    }

    /** Escribe el CSV (con BOM) en el flujo; no lo cierra. */
    public static void write(List<HistoryRow> rows, OutputStream out) throws IOException {
        Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        w.write(BOM);
        w.write(line(HEADER));
        for (HistoryRow r : rows) {
            w.write(line(List.of(
                    Long.toString(r.eventId()),
                    AppTime.format(r.occurredAt()),
                    r.source() == null ? "" : r.source().name(),
                    nz(r.connectorId()),
                    nz(r.bank()),
                    r.amount() == null ? "" : Long.toString(r.amount()),
                    nz(r.sender()),
                    nz(r.reference()),
                    statusText(r.status()),
                    nz(r.statusReason()),
                    AppTime.format(r.receivedAt()))));
        }
        w.flush();
    }

    public static void write(List<HistoryRow> rows, Path file) throws IOException {
        try (OutputStream out = Files.newOutputStream(file)) {
            write(rows, out);
        }
    }

    /** Contenido como cadena (incluye BOM). Para pruebas. */
    public static String toString(List<HistoryRow> rows) {
        try {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            write(rows, b);
            return b.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String statusText(EventStatus s) {
        return s == null ? "" : s.name();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String line(List<String> fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(fields.get(i)));
        }
        return sb.append(EOL).toString();
    }

    /** Neutraliza fórmulas y aplica comillas CSV (RFC 4180). */
    public static String escape(String value) {
        String v = neutralizeFormula(value == null ? "" : value);
        boolean quote = v.indexOf(',') >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0;
        if (quote) {
            return '"' + v.replace("\"", "\"\"") + '"';
        }
        return v;
    }

    /** Antepone una comilla simple a textos que Excel interpretaría como fórmula. */
    public static String neutralizeFormula(String v) {
        if (v.isEmpty()) {
            return v;
        }
        char c = v.charAt(0);
        if (c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r' || c == '\n') {
            return "'" + v;
        }
        return v;
    }
}
