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
        assertThat(csv).contains("José Peña").contains(",125000,").contains("01/03/2025 10:30:00");
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
        assertThat(CsvExporter.escape("=1+1")).isEqualTo("'=1+1");
        assertThat(CsvExporter.escape("+57300")).isEqualTo("'+57300");
        assertThat(CsvExporter.escape("-2")).isEqualTo("'-2");
        assertThat(CsvExporter.escape("@SUM(A1)")).isEqualTo("'@SUM(A1)");
        assertThat(CsvExporter.escape("\t=cmd")).startsWith("'");
        assertThat(CsvExporter.escape("normal")).isEqualTo("normal");
        List<HistoryRow> rows = List.of(UiTestData.row(1, "=HYPERLINK(\"http://x\")", 5L, "@evil", EventStatus.PARSEADO));
        String csv = CsvExporter.toString(rows);
        assertThat(csv).contains("\"'=HYPERLINK(\"\"http://x\"\")\"").contains(",'@evil,");
    }

    @Test
    void quotesCommasAndNewlines() {
        assertThat(CsvExporter.escape("a,b")).isEqualTo("\"a,b\"");
        assertThat(CsvExporter.escape("l1\nl2")).isEqualTo("\"l1\nl2\"");
        assertThat(CsvExporter.escape("di\"jo")).isEqualTo("\"di\"\"jo\"");
        assertThat(CsvExporter.escape(null)).isEmpty();
    }
}
