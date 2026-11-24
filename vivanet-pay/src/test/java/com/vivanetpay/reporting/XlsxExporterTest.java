package com.vivanetpay.reporting;

import com.vivanetpay.domain.EventStatus;
import com.vivanetpay.ui.UiTestData;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class XlsxExporterTest {
    private static Map<String, byte[]> unzip(byte[] xlsx) throws Exception {
        Map<String, byte[]> parts = new HashMap<>();
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(xlsx), StandardCharsets.UTF_8)) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                z.transferTo(b);
                parts.put(e.getName(), b.toByteArray());
            }
        }
        return parts;
    }

    private static Document parse(byte[] xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return f.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
    }

    private static List<Element> cells(Document d, int row) {
        NodeList rows = d.getElementsByTagName("row");
        Element r = (Element) rows.item(row);
        List<Element> out = new ArrayList<>();
        NodeList cs = r.getElementsByTagName("c");
        for (int i = 0; i < cs.getLength(); i++) {
            out.add((Element) cs.item(i));
        }
        return out;
    }

    @Test
    void paqueteValidoConPartesXmlBienFormadas() throws Exception {
        byte[] x = XlsxExporter.toBytes(List.of(UiTestData.row(1, "Nequi", 125000L, "Ana", EventStatus.VALIDADO_POR_FUENTE)));
        Map<String, byte[]> parts = unzip(x);
        assertThat(parts.keySet()).containsExactlyInAnyOrder("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml",
                "xl/_rels/workbook.xml.rels", "xl/styles.xml", "xl/worksheets/sheet1.xml");
        for (byte[] p : parts.values()) {
            assertThat(parse(p)).isNotNull();
        }
        assertThat(new String(parts.get("[Content_Types].xml"), StandardCharsets.UTF_8)).contains("spreadsheetml.sheet.main+xml");
    }

    @Test
    void encabezadosMontosNumericosYTildes() throws Exception {
        byte[] x = XlsxExporter.toBytes(List.of(
                UiTestData.row(7, "Bancolombia", 1250000L, "José Peña Ñandú", EventStatus.VALIDADO_POR_FUENTE),
                UiTestData.row(8, "Nequi", null, "Ana", EventStatus.PARSEADO)));
        Document sheet = parse(unzip(x).get("xl/worksheets/sheet1.xml"));
        List<Element> header = cells(sheet, 0);
        assertThat(header).hasSize(CsvExporter.HEADER.size());
        assertThat(header.get(0).getTextContent()).isEqualTo("ID evento");
        assertThat(header.get(5).getTextContent()).isEqualTo("Monto (COP)");
        assertThat(header).allSatisfy(c -> assertThat(c.getAttribute("s")).isEqualTo("1")); // negrita
        List<Element> r1 = cells(sheet, 1);
        assertThat(r1.get(0).getAttribute("t")).isEmpty(); // número
        assertThat(r1.get(0).getTextContent()).isEqualTo("7");
        Element amount = r1.get(5);
        assertThat(amount.getAttribute("r")).isEqualTo("F2");
        assertThat(amount.getAttribute("t")).isEmpty();
        assertThat(amount.getAttribute("s")).isEqualTo("2"); // #,##0
        assertThat(amount.getTextContent()).isEqualTo("1250000");
        assertThat(r1.stream().map(Element::getTextContent)).contains("José Peña Ñandú");
        // sin monto: la celda F3 no existe (no se escribe cadena vacía ni 0)
        assertThat(cells(sheet, 2).stream().map(e -> e.getAttribute("r"))).doesNotContain("F3");
        String styles = new String(unzip(x).get("xl/styles.xml"), StandardCharsets.UTF_8);
        assertThat(styles).contains("<b/>").contains("numFmtId=\"3\"");
    }

    @Test
    void textosConSignosDeFormulaSonCadenasNuncaFormulas() throws Exception {
        byte[] x = XlsxExporter.toBytes(List.of(
                UiTestData.row(1, "=HYPERLINK(\"http://x\")", 5L, "@evil", EventStatus.PARSEADO),
                UiTestData.row(2, "+57300", 5L, "-2+3", EventStatus.PARSEADO),
                UiTestData.row(3, "  =1+1", 5L, "a <b> & \"c\" 'd'", EventStatus.PARSEADO)));
        byte[] sheetBytes = unzip(x).get("xl/worksheets/sheet1.xml");
        String sheetText = new String(sheetBytes, StandardCharsets.UTF_8);
        assertThat(sheetText).doesNotContain("<f>").doesNotContain("<f ");
        Document sheet = parse(sheetBytes);
        assertThat(sheet.getElementsByTagName("f").getLength()).isZero();
        List<Element> r1 = cells(sheet, 1);
        Element bank = r1.get(4);
        assertThat(bank.getAttribute("t")).isEqualTo("inlineStr");
        assertThat(bank.getAttribute("s")).isEqualTo("3"); // quotePrefix + formato texto
        assertThat(bank.getTextContent()).isEqualTo("=HYPERLINK(\"http://x\")");
        assertThat(r1.get(6).getAttribute("t")).isEqualTo("inlineStr");
        assertThat(r1.get(6).getTextContent()).isEqualTo("@evil");
        assertThat(cells(sheet, 2).get(4).getTextContent()).isEqualTo("+57300");
        assertThat(cells(sheet, 2).get(6).getTextContent()).isEqualTo("-2+3");
        assertThat(cells(sheet, 3).get(6).getTextContent()).isEqualTo("a <b> & \"c\" 'd'");
        assertThat(unzip(x).get("xl/styles.xml")).asString(StandardCharsets.UTF_8).contains("quotePrefix=\"1\"");
    }

    @Test
    void caracteresInvalidosEnXmlSeEliminanYElDocumentoSigueSiendoValido() throws Exception {
        String dirty = "ok\u0000\u0001\u000B\uFFFE\uD800fin\uD83D\uDE00";
        assertThat(XlsxExporter.xmlEscape(dirty)).isEqualTo("okfin\uD83D\uDE00");
        byte[] x = XlsxExporter.toBytes(List.of(UiTestData.row(1, dirty, 1L, "x\u0008y", EventStatus.PARSEADO)));
        Document sheet = parse(unzip(x).get("xl/worksheets/sheet1.xml"));
        assertThat(cells(sheet, 1).get(4).getTextContent()).isEqualTo("okfin\uD83D\uDE00");
        assertThat(cells(sheet, 1).get(6).getTextContent()).isEqualTo("xy");
    }

    @Test
    void listaVaciaGeneraSoloEncabezadoYColumnasConAncho() throws Exception {
        Document sheet = parse(unzip(XlsxExporter.toBytes(List.of())).get("xl/worksheets/sheet1.xml"));
        assertThat(sheet.getElementsByTagName("row").getLength()).isEqualTo(1);
        assertThat(sheet.getElementsByTagName("col").getLength()).isEqualTo(CsvExporter.HEADER.size());
        assertThat(XlsxExporter.col(0)).isEqualTo("A");
        assertThat(XlsxExporter.col(25)).isEqualTo("Z");
        assertThat(XlsxExporter.col(26)).isEqualTo("AA");
    }
}
