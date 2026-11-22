package com.vivanetpay.observability;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class RedactorTest {

    @Test
    void enmascaraTokensYSecretos() {
        String in = "Authorization: Bearer ya29.a0AfH6SMBx-abc.def refresh_token=1//0gAbCdEf-123 "
                + "{\"client_secret\":\"GOCSPX-supersecreto\",\"password\": \"p4ss\"} api_key=AKIA123 "
                + "jwt eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NSJ9.c2lnbmF0dXJh";
        String out = Redactor.redact(in);
        assertThat(out).doesNotContain("ya29.a0AfH6SMBx", "1//0gAbCdEf", "GOCSPX-supersecreto", "p4ss", "AKIA123",
                "eyJhbGciOiJIUzI1NiJ9");
        assertThat(out).startsWith("Authorization: ***");
        assertThat(Redactor.redact("header Bearer abc.def-123")).isEqualTo("header Bearer ***");
    }

    @Test
    void enmascaraCorreosYNumerosLargos() {
        String out = Redactor.redact("De juan.perez@gmail.com cuenta 3001234567 doc 1020304050607");
        assertThat(out).contains("j***@gmail.com").doesNotContain("juan.perez");
        assertThat(out).contains("***4567").doesNotContain("3001234567");
        assertThat(out).doesNotContain("1020304050607");
    }

    @Test
    void conservaTextoNormalEImportesFormateados() {
        String s = "Evento #12 persistido con estado PARSEADO por $1.250.000 statusCode=200 id 550e8400-e29b-41d4-a716-446655440000";
        assertThat(Redactor.redact(s)).isEqualTo(s);
        assertThat(Redactor.redact(null)).isNull();
    }

    @Test
    void conversorDeLogbackRedactaElMensaje() {
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        LoggingEvent ev = new LoggingEvent("x", ctx.getLogger("t"), Level.INFO, "token={} correo={}", null,
                new Object[]{"abc123secreto", "ana@correo.com"});
        RedactingMessageConverter conv = new RedactingMessageConverter();
        String out = conv.convert(ev);
        assertThat(out).doesNotContain("abc123secreto").contains("a***@correo.com");
    }

    @Test
    void correlacionEnMdc() {
        try (var c = Correlation.open("cid-1")) {
            assertThat(MDC.get(Correlation.MDC_KEY)).isEqualTo("cid-1");
            try (var inner = Correlation.open("cid-2")) {
                assertThat(Correlation.current()).isEqualTo("cid-2");
            }
            assertThat(Correlation.current()).isEqualTo("cid-1");
        }
        assertThat(Correlation.current()).isNull();
    }
}
