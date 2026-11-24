package com.vivanetpay.parsing;

import com.vivanetpay.domain.EventSource;
import com.vivanetpay.domain.RawEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ParsersFixturesTest {

    private static final ParserRegistry REGISTRY = DefaultParsers.registry();

    record Fixture(String id, EventSource source, String from, String subject, String text, String expected) {
        @Override
        public String toString() {
            return id;
        }
    }

    static Stream<Fixture> fixtures() throws IOException {
        List<Fixture> out = new ArrayList<>();
        try (InputStream in = ParsersFixturesTest.class.getResourceAsStream("/fixtures/parsers-v1.psv")) {
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] f = line.split("\\|", -1);
                out.add(new Fixture(f[0], EventSource.valueOf(f[1]), f[2], f[3],
                        f[4].replace("\\n", "\n").replace("\\u00A0", " "), f[5]));
            }
        }
        return out.stream();
    }

    private static RawEvent event(Fixture f) {
        return RawEvent.builder(f.source(), "test")
                .externalEventId(f.id())
                .sender(f.from().isEmpty() ? null : f.from())
                .subject(f.subject().isEmpty() ? null : f.subject())
                .rawText(f.text())
                .headers(Map.of())
                .build();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    void fixture(Fixture f) {
        ParseResult r = REGISTRY.parse(event(f));
        String[] e = f.expected().split(":", -1);
        switch (e[0]) {
            case "ok" -> {
                assertThat(r.recognized()).as(r.reason()).isTrue();
                assertThat(r.parserId()).isEqualTo(e[1]);
                assertThat(r.amount()).isEqualTo(Long.parseLong(e[2]));
                assertThat(r.confidence()).isGreaterThanOrEqualTo(0.9);
                assertThat(r.sender()).isEqualTo(e[3].isEmpty() ? null : e[3]);
                if (!e[4].isEmpty()) {
                    assertThat(r.reference()).isEqualTo(e[4]);
                }
                assertThat(r.reason()).contains("plantilla " + e[1]);
            }
            case "low" -> {
                assertThat(r.hasAmount()).as(r.reason()).isTrue();
                assertThat(r.amount()).isEqualTo(Long.parseLong(e[1]));
                assertThat(r.confidence()).isLessThan(0.9);
                assertThat(r.reason()).isNotBlank();
            }
            case "invalid" -> {
                assertThat(r.recognized()).isTrue();
                assertThat(r.amount()).isLessThanOrEqualTo(0L);
            }
            case "none" -> assertThat(r.hasAmount()).as(r.reason()).isFalse();
            default -> throw new IllegalStateException(f.expected());
        }
    }

    @Test
    void fixtureCountAtLeastThirty() throws IOException {
        assertThat(fixtures().count()).isGreaterThanOrEqualTo(30);
    }

    @Test
    void unverifiedSenderReason() {
        RawEvent ev = RawEvent.builder(EventSource.GMAIL, "g").sender("Nequi <a@gmail.com>")
                .rawText("JUAN PEREZ te envió $50.000").build();
        ParseResult r = REGISTRY.parse(ev);
        assertThat(r.confidence()).isEqualTo(0.3);
        assertThat(r.reason()).contains("remitente no verificado");
    }

    @Test
    void configurableDomainAndAuthFailure() {
        NequiEmailParser p = new NequiEmailParser(List.of("mi-banco.test"));
        RawEvent ok = RawEvent.builder(EventSource.GMAIL, "g").sender("x@mi-banco.test")
                .rawText("Nequi: JUAN PEREZ te envió $50.000").build();
        assertThat(p.parse(ok).confidence()).isGreaterThanOrEqualTo(0.9);
        RawEvent failed = RawEvent.builder(EventSource.GMAIL, "g").sender("x@mi-banco.test")
                .headers(Map.of("Authentication-Results", "mx; dkim=fail"))
                .rawText("Nequi: JUAN PEREZ te envió $50.000").build();
        assertThat(p.parse(failed).reason()).contains("remitente no verificado");
    }

    @Test
    void versionedAndRegistered() {
        assertThat(DefaultParsers.all()).allSatisfy(p -> {
            assertThat(p).isInstanceOf(VersionedParser.class);
            assertThat(((VersionedParser) p).version()).matches("\\d+\\.\\d+\\.\\d+");
            assertThat(p.id()).matches(".*-v\\d+");
        });
        assertThat(REGISTRY.parsers()).hasSize(3);
    }

    @Test
    void neverThrowsOnGarbage() {
        for (String s : new String[]{"", "$", "te envió", "te envió $", "recibiste $ de", "\u0000\u0001", "te envió $" + "9".repeat(40)}) {
            for (EventSource src : new EventSource[]{EventSource.ANDROID_NEQUI, EventSource.GMAIL}) {
                ParseResult r = REGISTRY.parse(RawEvent.builder(src, "t").rawText(s).sender("nequi bancolombia").build());
                assertThat(r.hasAmount() && r.amount() > 0 && r.confidence() >= 0.9).isFalse();
            }
        }
    }
}
