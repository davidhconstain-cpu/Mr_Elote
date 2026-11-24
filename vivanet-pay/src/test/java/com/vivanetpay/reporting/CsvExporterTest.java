package com.vivanetpay.reporting;

import com.vivanetpay.domain.EventStatus;
import com.vivanetpay.ui.UiTestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvExporterTest {

    @Test
    void startsWithBomAndKeepsAccents() throws Exception {
        List<HistoryRow> rows = List.of(UiTestData.row(1, "Bancolombia", 125000L, "José Peña", EventStatus.VALIDADO_POR_FUENTE));
        String csv = CsvExporter.toString(rows);
        assertThat(csv.charAt(0)).isEqualTo('\uFEFF');
        assertThat(csv).contains("José Peña").contains(";125000;").contains("01/03/2025 10:30:00");
        assertThat(csv).contains("Fecha y hora (America/Bogota)");
    }

    @Test
    void writesBomBytesAndUtf8(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("h.csv");
        CsvExporter.write(List.of(UiTestData.row(1, "Ñandú", 1L, "x", EventStatus.PARSEADO)), f);
        byte[] b = Files.readAllBytes(f);
        assertThat(b[0] & 0xFF).isEqualTo(0xEF);
        assertThat(b[1] & 0xFF).isEqualTo(0xBB);
        assertThat(b[2] & 0xFF).isEqualTo(0xBF);
        assertThat(new String(b, StandardCharsets.UTF_8)).contains("Ñandú");
    }

    @Test
    void neutralizesFormulaInjection() {
        // OWASP: comilla simple inicial y campo entrecomillado
        assertThat(CsvExporter.escape("=1+1")).isEqualTo("\"'=1+1\"");
        assertThat(CsvExporter.escape("+57300")).isEqualTo("\"'+57300\"");
        assertThat(CsvExporter.escape("-2")).isEqualTo("\"'-2\"");
        assertThat(CsvExporter.escape("@SUM(A1)")).isEqualTo("\"'@SUM(A1)\"");
        assertThat(CsvExporter.escape("\t=cmd")).startsWith("\"'");
        assertThat(CsvExporter.escape("\r=cmd")).startsWith("\"'");
        assertThat(CsvExporter.escape("normal")).isEqualTo("normal");
        List<HistoryRow> rows = List.of(UiTestData.row(1, "=HYPERLINK(\"http://x\")", 5L, "@evil", EventStatus.PARSEADO));
        String csv = CsvExporter.toString(rows);
        assertThat(csv).contains("\"'=HYPERLINK(\"\"http://x\"\")\"").contains(";\"'@evil\";");
    }

    @Test
    void neutralizesFormulasHiddenBehindLeadingSpacesAndFullWidthSigns() {
        assertThat(CsvExporter.neutralizeFormula("  =1+1")).startsWith("'");
        assertThat(CsvExporter.neutralizeFormula("\u00A0@SUM(A1)")).startsWith("'");
        assertThat(CsvExporter.neutralizeFormula("\uFF1DHYPERLINK(1)")).startsWith("'");
        assertThat(CsvExporter.neutralizeFormula("\uFF0B1")).startsWith("'");
        // texto normal con signos en medio no se toca
        assertThat(CsvExporter.neutralizeFormula("Juan = José - 1")).isEqualTo("Juan = José - 1");
        assertThat(CsvExporter.neutralizeFormula("  Ana")).isEqualTo("  Ana");
        assertThat(CsvExporter.neutralizeFormula("")).isEmpty();
    }

    @Test
    void quotesCommasAndNewlines() {
        assertThat(CsvExporter.escape("a,b")).isEqualTo("\"a,b\"");
        assertThat(CsvExporter.escape("l1\nl2")).isEqualTo("\"l1\nl2\"");
        assertThat(CsvExporter.escape("di\"jo")).isEqualTo("\"di\"\"jo\"");
        assertThat(CsvExporter.escape(null)).isEmpty();
    }

    @Test
    void separadorPorDefectoEsPuntoYComaYEsConfigurable() {
        List<HistoryRow> rows = List.of(UiTestData.row(1, "Nequi", 1250000L, "Ana; Pérez", EventStatus.PARSEADO));
        String csv = CsvExporter.toString(rows);
        assertThat(csv.split("\r\n")[0]).contains("ID evento;Fecha y hora").doesNotContain(",");
        assertThat(csv).contains(";1250000;").doesNotContain("1.250.000").doesNotContain("1,250,000");
        assertThat(csv).contains("\"Ana; Pérez\"").endsWith("\r\n");
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        try {
            CsvExporter.write(rows, b, ',');
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        String comma = b.toString(StandardCharsets.UTF_8);
        assertThat(comma).contains("ID evento,Fecha y hora").contains(",1250000,").contains("Ana; Pérez");
        assertThat(CsvExporter.parseSeparator(null)).isEqualTo(';');
        assertThat(CsvExporter.parseSeparator(",")).isEqualTo(',');
        assertThat(CsvExporter.parseSeparator("tab")).isEqualTo('\t');
        assertThat(CsvExporter.parseSeparator("xx")).isEqualTo(';');
    }

    @Test
    void formulasSeNeutralizanTambienConPuntoYComa() {
        assertThat(CsvExporter.escape("=1+1;2", ';')).isEqualTo("\"'=1+1;2\"");
        assertThat(CsvExporter.escape("a;b", ';')).isEqualTo("\"a;b\"");
    }
}
