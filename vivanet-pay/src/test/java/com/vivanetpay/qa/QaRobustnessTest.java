package com.vivanetpay.qa;

import com.vivanetpay.application.IngestionResult;
import com.vivanetpay.domain.CopAmount;
import com.vivanetpay.domain.EventSource;
import com.vivanetpay.domain.EventStatus;
import com.vivanetpay.domain.RawEvent;
import com.vivanetpay.parsing.DefaultParsers;
import com.vivanetpay.parsing.ParseResult;
import com.vivanetpay.parsing.ParserRegistry;
import com.vivanetpay.reporting.CsvExporter;
import com.vivanetpay.reporting.HistoryFilter;
import com.vivanetpay.support.TestApp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * QA de robustez: montos límite, Unicode raro, texto hostil (ReDoS) y exportación segura, de extremo a extremo con la
 * base real (RF-007, RF-009, RN-006, RN-007, CP-010, RF-018).
 */
class QaRobustnessTest {
    @TempDir
    Path dir;
    TestApp app;

    @BeforeEach
    void setUp() {
        app = TestApp.create(dir);
    }

    @AfterEach
    void tearDown() {
        app.ctx.close();
    }

    private IngestionResult ingest(String id, String text) {
        return app.ctx.ingestionService().ingest(app.nequi(id, text));
    }

    // ----------------------------------------------------------------- montos límite (CP-010, RN-007)

    @Test
    void montosLimiteNuncaSeContabilizanSilenciosamente() {
        // dentro del rango razonable: aceptado como posible pago (nunca confirmado)
        assertThat(ingest("m-1", "Ana te envió $1").status()).isEqualTo(EventStatus.PARSEADO);
        assertThat(ingest("m-2", "Ana te envió $" + CopAmount.MAX_REASONABLE).status()).isEqualTo(EventStatus.PARSEADO);
        long accepted = app.ctx.transactionRepository().count();
        assertThat(accepted).isEqualTo(2);

        // cero, negativo y por encima del tope: RECHAZADO, sin movimiento
        for (String bad : new String[]{"$0", "$000", "-1", "-5.000", "$" + (CopAmount.MAX_REASONABLE + 1),
                "$99.999.999.999.999"}) {
            IngestionResult r = ingest("bad-" + bad, "Ana te envió " + bad);
            assertThat(r.status()).as(bad).isEqualTo(EventStatus.RECHAZADO);
            assertThat(r.transactionId()).as(bad).isNull();
        }
        // desbordamiento de long / demasiados dígitos / basura: sin importe => revisión manual, sin movimiento
        for (String huge : new String[]{"$9223372036854775807", "$9223372036854775808", "$" + "9".repeat(40),
                "$1.2.3", "$1,5,5,5", "$10.000,99"}) {
            IngestionResult r = ingest("huge-" + huge, "Ana te envió " + huge);
            assertThat(r.status()).as(huge).isEqualTo(EventStatus.PENDIENTE_REVISION);
            assertThat(r.transactionId()).as(huge).isNull();
        }
        assertThat(app.ctx.transactionRepository().count()).isEqualTo(accepted);
        // el tablero no desborda ni mezcla: solo suma lo contabilizado
        assertThat(app.ctx.dashboardService().today().unconfirmedTotal()).isEqualTo(1L + CopAmount.MAX_REASONABLE);
        assertThat(app.ctx.dashboardService().today().confirmedTotal()).isZero();
    }

    @Test
    void sumaDeMuchosMontosMaximosNoDesbordaElTablero() {
        for (int i = 0; i < 50; i++) {
            ingest("sum-" + i, "Ana" + i + " te envió $" + CopAmount.MAX_REASONABLE);
        }
        assertThat(app.ctx.dashboardService().today().unconfirmedTotal()).isEqualTo(50 * CopAmount.MAX_REASONABLE);
    }

    // ----------------------------------------------------------------- Unicode en el flujo completo

    @Test
    void textoConEmojiUnicodeRaroYControlSeGuardaSinPerderElEventoNiRomperLaBase() {
        String[] texts = {
                "Ana 😀 te envió $5.000 🎉",
                "‮evil‬ te envió $6.000",
                "é́ te envió $7.000 ​‍﻿",
                "Zoë Ñandú te envió $8.000 ‒ ‑ —",
                "Ana te envió $9.000 \u0000 con NUL",
                "Ana te envió $1.000 \uD800 sustituto suelto",
                "𝒜 te envió $2.000",
        };
        int n = 0;
        for (String t : texts) {
            IngestionResult r = ingest("u-" + n++, t);
            assertThat(r.isPersisted()).as("persistido: " + t).isTrue();
            assertThat(r.status()).as(t).isNotEqualTo(EventStatus.ERROR);
            assertThat(r.status().isConfirmed()).isFalse();
        }
        assertThat(app.ctx.maintenance().integrityCheck()).isEmpty();
        // lectura de vuelta del emoji: sin pérdida
        assertThat(app.ctx.eventRepository().findById(1L).orElseThrow().rawText())
                .isEqualTo("Ana 😀 te envió $5.000 🎉");
    }

    @Test
    void exportacionCsvNeutralizaFormulasQueVienenDeNotificacionesReales() {
        ingest("csv-1", "=HYPERLINK(\"http://evil\",\"x\") te envió $5.000 Ref: AB123");
        ingest("csv-2", "@SUM(1+1) te envió $6.000");
        ingest("csv-3", "+cmd|' /C calc'!A0 te envió $7.000");
        String csv = CsvExporter.toString(app.ctx.historyService().search(HistoryFilter.all()));
        for (String line : csv.split("\r\n")) {
            for (String cell : line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1)) {
                String c = cell.startsWith("\"") ? cell.substring(1) : cell;
                assertThat(c).as("celda: " + cell).doesNotStartWith("=").doesNotStartWith("@").doesNotStartWith("+");
            }
        }
        assertThat(csv).contains("'=HYPERLINK").contains("'@SUM").contains("'+cmd");
    }

    // ----------------------------------------------------------------- texto hostil / ReDoS en parsers

    @Test
    void parsersNoSeCuelganConTextoHostilDeTamanoMaximo() {
        ParserRegistry registry = DefaultParsers.registry();
        int max = RawEvent.MAX_RAW_TEXT_LENGTH;
        List<String> hostile = List.of(
                "1,".repeat(max / 2),
                "1.".repeat(max / 2),
                "te envió $" + "1.".repeat(max / 2),
                "a ".repeat(max / 2) + "te envió $5.000",
                "a".repeat(max - 20) + " te envió $5",
                "recibiste $" + "9,".repeat(max / 2) + " de",
                "de ".repeat(max / 3),
                "recibiste una transferencia de " + "ana ".repeat(max / 4),
                "Ref: " + "a1".repeat(max / 2),
                "'’-".repeat(max / 3) + " te envió $5",
                "😀 ".repeat(max / 3) + "te envió $5.000",
                ". ".repeat(max / 2) + "Ana te envió $5.000",
                "$ ".repeat(max / 2),
                "COP ".repeat(max / 4) + "$5");
        for (EventSource src : new EventSource[]{EventSource.ANDROID_NEQUI, EventSource.GMAIL}) {
            for (String text : hostile) {
                String t = text.length() > max ? text.substring(0, max) : text;
                RawEvent ev = RawEvent.builder(src, "qa").rawText(t).sender("nequi bancolombia").subject("Nequi").build();
                ParseResult r = assertTimeoutPreemptively(Duration.ofSeconds(5), () -> registry.parse(ev),
                        () -> "parser lento con " + src + " y texto de " + t.length() + " chars: "
                                + t.substring(0, Math.min(30, t.length())));
                assertThat(r).isNotNull();
            }
        }
    }

    @Test
    void parsersUnicodeNuncaProducenImporteConfiableConDigitosNoAscii() {
        ParserRegistry registry = DefaultParsers.registry();
        for (String s : new String[]{"Ana te envió $５０.０００", "Ana te envió $٥٠.٠٠٠",
                "Ana te envió $5​0.000", "Ana te envió $ 50.000 "}) {
            ParseResult r = registry.parse(RawEvent.builder(EventSource.ANDROID_NEQUI, "qa").rawText(s).build());
            // o no se reconoce, o se extrae el importe correcto, o queda con confianza baja (=> revisión manual)
            assertThat(r.hasAmount() && r.amount() > 0 && r.amount() != 50_000L && r.confidence() >= 0.9)
                    .as("importe inesperado y confiable para " + s + ": " + r.amount()).isFalse();
        }
    }

    @Test
    void importeSeparadoPorEspacioOCaracterInvisibleBajaLaConfianza() {
        ParserRegistry registry = DefaultParsers.registry();
        for (String s : new String[]{"Ana te envió $5\u200B0.000", "Ana te envió $50 000", "Ana te envió $50\u00A0000",
                "Ana te envió $5\uFEFF0.000", "Ana te envió $50.000\u200B000"}) {
            ParseResult r = registry.parse(RawEvent.builder(EventSource.ANDROID_NEQUI, "qa").rawText(s).build());
            assertThat(r.confidence()).as(s).isLessThan(0.9);
        }
        // y los formatos normales siguen siendo confiables
        for (String s : new String[]{"Ana te envió $50.000", "Ana te envió $50.000 el 22 de noviembre", "Ana te envió $1.250.000 Ref: M123"}) {
            assertThat(registry.parse(RawEvent.builder(EventSource.ANDROID_NEQUI, "qa").rawText(s).build()).confidence())
                    .as(s).isGreaterThanOrEqualTo(0.9);
        }
    }

    @Test
    void correoConDisplayNameQueImitaAlBancoNoVerificaElOrigen() {
        ParserRegistry registry = DefaultParsers.registry();
        // el nombre visible contiene una dirección legítima pero la real es del atacante; subdominios engañosos
        for (String from : new String[]{
                "\"alertas@bancolombia.com.co\" <phisher@evil.example>",
                "Bancolombia <x@bancolombia.com.co.evil.example>",
                "Bancolombia <x@evilbancolombia.com.co>",
                "x@bancolombia.com.co@evil.example"}) {
            ParseResult r = registry.parse(RawEvent.builder(EventSource.GMAIL, "qa").sender(from).headers(Map.of())
                    .subject("Bancolombia: transferencia").rawText("Bancolombia le informa que recibió una transferencia por $50.000 de JUAN PEREZ.")
                    .build());
            if (r.recognized()) {
                assertThat(r.confidence()).as(from).isLessThan(0.9);
            }
        }
    }
}
