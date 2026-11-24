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
 * DP-08 decidida: separador {@code ;} por defecto (Excel en configuración regional colombiana espera punto y coma;
 * configurable con {@code export.csv.separator}) y, además, Excel .xlsx ({@link XlsxExporter}). El archivo es UTF-8
 * con BOM (Excel en Windows lo reconoce y respeta las tildes), fin de línea CRLF, importes en pesos enteros sin
 * separador de miles (Excel los suma) y horas en America/Bogota.
 * <p>
 * Protección contra inyección de fórmulas (CSV injection): todo texto libre que empiece por {@code = + - @},
 * tabulación, retorno de carro o salto de línea se antepone con una comilla simple.
 */
public final class CsvExporter {
    public static final char BOM = '﻿';
    public static final String EOL = "\r\n";
    public static final char DEFAULT_SEPARATOR = ';';
    static final List<String> HEADER = List.of("ID evento", "Fecha y hora (America/Bogota)", "Fuente", "Conector",
            "Entidad", "Monto (COP)", "Remitente", "Referencia", "Estado", "Motivo del estado", "Recibido (America/Bogota)");

    private CsvExporter() {
    }

    /** Escribe el CSV (con BOM) en el flujo; no lo cierra. */
    public static void write(List<HistoryRow> rows, OutputStream out) throws IOException {
        write(rows, out, DEFAULT_SEPARATOR);
    }

    /** Separador desde texto de configuración ({@code export.csv.separator}): uno de ; , tab |; otro valor => ';'. */
    public static char parseSeparator(String configured) {
        if (configured == null || configured.isEmpty()) {
            return DEFAULT_SEPARATOR;
        }
        String c = configured.strip();
        if (c.equalsIgnoreCase("tab") || c.equals("\\t") || configured.equals("\t")) {
            return '\t';
        }
        return c.length() == 1 && ";,|".indexOf(c.charAt(0)) >= 0 ? c.charAt(0) : DEFAULT_SEPARATOR;
    }

    public static void write(List<HistoryRow> rows, OutputStream out, char separator) throws IOException {
        Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        w.write(BOM);
        w.write(line(HEADER, separator));
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
                    AppTime.format(r.receivedAt())), separator));
        }
        w.flush();
    }

    public static void write(List<HistoryRow> rows, Path file) throws IOException {
        write(rows, file, DEFAULT_SEPARATOR);
    }

    public static void write(List<HistoryRow> rows, Path file, char separator) throws IOException {
        try (OutputStream out = Files.newOutputStream(file)) {
            write(rows, out, separator);
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

    private static String line(List<String> fields, char sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                sb.append(sep);
            }
            sb.append(escape(fields.get(i), sep));
        }
        return sb.append(EOL).toString();
    }

    /**
     * Neutraliza fórmulas y aplica comillas CSV (RFC 4180). Un campo neutralizado siempre va entre comillas
     * (recomendación OWASP "CSV Injection": comilla simple inicial + campo entrecomillado).
     */
    public static String escape(String value) {
        return escape(value, DEFAULT_SEPARATOR);
    }

    public static String escape(String value, char separator) {
        String raw = value == null ? "" : value;
        String v = neutralizeFormula(raw);
        boolean neutralized = v.length() != raw.length();
        boolean quote = neutralized || v.indexOf(separator) >= 0 || v.indexOf(',') >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0
                || v.indexOf('\r') >= 0;
        if (quote) {
            return '"' + v.replace("\"", "\"\"") + '"';
        }
        return v;
    }

    /**
     * Antepone una comilla simple a textos que Excel, LibreOffice o Google Sheets interpretarían como fórmula:
     * primer carácter significativo {@code = + - @}, tabulación, CR o LF (OWASP), sus equivalentes de ancho
     * completo ({@code ＝ ＋ － ＠}) y los mismos casos precedidos de espacios o caracteres de control, que algunas
     * hojas de cálculo ignoran al evaluar.
     */
    public static String neutralizeFormula(String v) {
        int i = 0;
        while (i < v.length() && (Character.isWhitespace(v.charAt(i)) || Character.isISOControl(v.charAt(i))
                || v.charAt(i) == '\u00A0' || v.charAt(i) == '\uFEFF') && !isLeadingTrigger(v.charAt(i))) {
            i++;
        }
        if (i < v.length() && (isLeadingTrigger(v.charAt(i)) || isFormulaStart(v.charAt(i)))) {
            return "'" + v;
        }
        return v;
    }

    private static boolean isLeadingTrigger(char c) {
        return c == '\t' || c == '\r' || c == '\n';
    }

    private static boolean isFormulaStart(char c) {
        return switch (c) {
            case '=', '+', '-', '@', '\uFF1D', '\uFF0B', '\uFF0D', '\uFF20' -> true;
            default -> false;
        };
    }
}
