package com.vivanetpay.reporting;

import com.vivanetpay.domain.AppTime;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Exportación a Excel (.xlsx) del historial (DP-08), sin dependencias: se genera a mano el paquete OOXML mínimo
 * (SpreadsheetML) con {@link ZipOutputStream}: tipos de contenido, relaciones, libro, una hoja, estilos.
 * <ul>
 *   <li>Encabezado en negrita, fila fija y autofiltro; anchos de columna definidos.</li>
 *   <li>ID e importe son números reales; el importe usa el formato {@code #,##0} (se suma sin conversiones).</li>
 *   <li>Todo texto va como cadena en línea ({@code t="inlineStr"}), NUNCA como fórmula. Además, el texto que empieza
 *       por {@code = + - @} (incl. anchos completos) o espacios/controles previos se marca con {@code quotePrefix}
 *       para que Excel lo trate como texto también al editar la celda.</li>
 *   <li>Se eliminan los caracteres no válidos en XML 1.0 y se escapan {@code & < > " '}. Máximo 32 767 caracteres
 *       por celda (límite de Excel).</li>
 * </ul>
 */
public final class XlsxExporter {
    public static final String CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    static final int MAX_CELL_CHARS = 32_767;
    // Estilos (índices de cellXfs en styles.xml)
    static final int STYLE_DEFAULT = 0;
    static final int STYLE_HEADER = 1;
    static final int STYLE_AMOUNT = 2;
    static final int STYLE_TEXT_QUOTED = 3;
    private static final double[] WIDTHS = {10, 22, 16, 18, 16, 16, 24, 18, 22, 48, 22};
    private static final long FIXED_TIME = 0L;

    private XlsxExporter() {
    }

    public static void write(List<HistoryRow> rows, Path file) throws IOException {
        try (OutputStream out = Files.newOutputStream(file)) {
            write(rows, out);
        }
    }

    public static byte[] toBytes(List<HistoryRow> rows) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            write(rows, b);
            return b.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Escribe el libro en el flujo (no lo cierra). */
    public static void write(List<HistoryRow> rows, OutputStream out) throws IOException {
        ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8);
        entry(zip, "[Content_Types].xml", CONTENT_TYPES);
        entry(zip, "_rels/.rels", ROOT_RELS);
        entry(zip, "xl/workbook.xml", WORKBOOK);
        entry(zip, "xl/_rels/workbook.xml.rels", WORKBOOK_RELS);
        entry(zip, "xl/styles.xml", STYLES);
        entry(zip, "xl/worksheets/sheet1.xml", sheet(rows));
        zip.finish();
        zip.flush();
    }

    private static void entry(ZipOutputStream zip, String name, String xml) throws IOException {
        ZipEntry e = new ZipEntry(name);
        e.setTime(FIXED_TIME);
        zip.putNextEntry(e);
        zip.write(xml.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    static String sheet(List<HistoryRow> rows) {
        StringBuilder sb = new StringBuilder(4096 + rows.size() * 400);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        int lastRow = rows.size() + 1;
        sb.append("<dimension ref=\"A1:").append(col(CsvExporter.HEADER.size() - 1)).append(lastRow).append("\"/>");
        sb.append("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>");
        sb.append("<sheetFormatPr defaultRowHeight=\"15\"/><cols>");
        for (int i = 0; i < WIDTHS.length; i++) {
            sb.append("<col min=\"").append(i + 1).append("\" max=\"").append(i + 1).append("\" width=\"")
                    .append(WIDTHS[i]).append("\" customWidth=\"1\"/>");
        }
        sb.append("</cols><sheetData>");
        // encabezado
        sb.append("<row r=\"1\">");
        for (int c = 0; c < CsvExporter.HEADER.size(); c++) {
            text(sb, c, 1, CsvExporter.HEADER.get(c), STYLE_HEADER);
        }
        sb.append("</row>");
        int r = 2;
        for (HistoryRow h : rows) {
            sb.append("<row r=\"").append(r).append("\">");
            number(sb, 0, r, h.eventId(), STYLE_DEFAULT);
            text(sb, 1, r, AppTime.format(h.occurredAt()), STYLE_DEFAULT);
            text(sb, 2, r, h.source() == null ? "" : h.source().name(), STYLE_DEFAULT);
            text(sb, 3, r, nz(h.connectorId()), STYLE_DEFAULT);
            text(sb, 4, r, nz(h.bank()), STYLE_DEFAULT);
            if (h.amount() != null) {
                number(sb, 5, r, h.amount(), STYLE_AMOUNT);
            }
            text(sb, 6, r, nz(h.sender()), STYLE_DEFAULT);
            text(sb, 7, r, nz(h.reference()), STYLE_DEFAULT);
            text(sb, 8, r, h.status() == null ? "" : h.status().name(), STYLE_DEFAULT);
            text(sb, 9, r, nz(h.statusReason()), STYLE_DEFAULT);
            text(sb, 10, r, AppTime.format(h.receivedAt()), STYLE_DEFAULT);
            sb.append("</row>");
            r++;
        }
        sb.append("</sheetData>");
        sb.append("<autoFilter ref=\"A1:").append(col(CsvExporter.HEADER.size() - 1)).append(lastRow).append("\"/>");
        sb.append("</worksheet>");
        return sb.toString();
    }

    private static void number(StringBuilder sb, int c, int r, long v, int style) {
        sb.append("<c r=\"").append(col(c)).append(r).append('"');
        if (style != STYLE_DEFAULT) {
            sb.append(" s=\"").append(style).append('"');
        }
        sb.append("><v>").append(v).append("</v></c>");
    }

    private static void text(StringBuilder sb, int c, int r, String value, int style) {
        String v = value == null ? "" : value;
        if (v.length() > MAX_CELL_CHARS) {
            v = v.substring(0, MAX_CELL_CHARS);
        }
        if (v.isEmpty()) {
            return;
        }
        int s = style;
        if (s == STYLE_DEFAULT && CsvExporter.neutralizeFormula(v).length() != v.length()) {
            s = STYLE_TEXT_QUOTED; // parece fórmula: sigue siendo cadena y Excel lo trata como texto al editar
        }
        sb.append("<c r=\"").append(col(c)).append(r).append("\" t=\"inlineStr\"");
        if (s != STYLE_DEFAULT) {
            sb.append(" s=\"").append(s).append('"');
        }
        sb.append("><is><t xml:space=\"preserve\">").append(xmlEscape(v)).append("</t></is></c>");
    }

    static String col(int index) {
        StringBuilder b = new StringBuilder();
        int n = index;
        do {
            b.insert(0, (char) ('A' + n % 26));
            n = n / 26 - 1;
        } while (n >= 0);
        return b.toString();
    }

    /** Escapa texto XML y elimina los caracteres no permitidos en XML 1.0 (incluye sustitutos sueltos). */
    static String xmlEscape(String s) {
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            boolean valid = cp == 0x9 || cp == 0xA || cp == 0xD || (cp >= 0x20 && cp <= 0xD7FF)
                    || (cp >= 0xE000 && cp <= 0xFFFD) || (cp >= 0x10000 && cp <= 0x10FFFF);
            if (!valid) {
                continue;
            }
            switch (cp) {
                case '&' -> b.append("&amp;");
                case '<' -> b.append("&lt;");
                case '>' -> b.append("&gt;");
                case '"' -> b.append("&quot;");
                case '\'' -> b.append("&apos;");
                default -> b.appendCodePoint(cp);
            }
        }
        return b.toString();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static final String CONTENT_TYPES = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
            + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
            + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
            + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
            + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
            + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
            + "</Types>";

    private static final String ROOT_RELS = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
            + "</Relationships>";

    private static final String WORKBOOK = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
            + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
            + "<bookViews><workbookView/></bookViews>"
            + "<sheets><sheet name=\"Historial\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>";

    private static final String WORKBOOK_RELS = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
            + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
            + "</Relationships>";

    private static final String STYLES = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
            + "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font>"
            + "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>"
            + "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills>"
            + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
            + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
            + "<cellXfs count=\"4\">"
            + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
            + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>"
            + "<xf numFmtId=\"3\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"
            + "<xf numFmtId=\"49\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\" quotePrefix=\"1\"/>"
            + "</cellXfs>"
            + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
            + "</styleSheet>";
}
