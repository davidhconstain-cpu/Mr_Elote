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

    // ---- QA: casos difíciles (RF-018, RNF-012) ----

    @Test
    void valoresConEspaciosYComillasSeEnmascaranCompletos() {
        String out = Redactor.redact("{\"password\": \"mi frase secreta larga\", \"x\":1} 'token': 'abc def ghi' "
                + "client_secret=\"GOCSPX con espacio\" \"pwd\":\"a\\\"b cola\"");
        assertThat(out).doesNotContain("frase", "secreta", "larga", "def ghi", "con espacio", "cola");
        assertThat(out).contains("\"x\":1");
    }

    @Test
    void clavesCamelCaseYConPrefijoSufijo() {
        String out = Redactor.redact("clientSecret=AbC123xyz refreshToken: \"r t 1\" X-Vivanet-Signature: v1=deadbeef "
                + "webhook_secret_previous=zzz9 accessToken=ya29.holaMundo cookie=SID=abc123");
        assertThat(out).doesNotContain("AbC123xyz", "r t 1", "deadbeef", "zzz9", "holaMundo", "abc123");
    }

    @Test
    void cabeceraAuthorizationConCualquierEsquema() {
        assertThat(Redactor.redact("Authorization: Token abc123def456")).doesNotContain("abc123def456");
        assertThat(Redactor.redact("authorization=ApiKey s3cr3t-valor")).doesNotContain("s3cr3t-valor");
        assertThat(Redactor.redact("Authorization: Basic dXNlcjpwYXNz")).doesNotContain("dXNlcjpwYXNz");
        assertThat(Redactor.redact("Authorization: Digest username=\"bob\", response=\"abcdef\""))
                .doesNotContain("abcdef");
    }

    @Test
    void firmasHexYBase64UrlLargasYUrlsConCodigos() {
        String hex = "a".repeat(30) + "0123456789abcdef0123456789abcdef0123";
        String b64url = "Zm9v-_Zm9v-_Zm9v-_Zm9v-_Zm9v-_Zm9v-_Zm9v-_Zm9v";
        String out = Redactor.redact("sig " + hex + " otra " + b64url
                + " https://app/cb?code=4/0AbCdEf&state=xyz&access_token=ya29.tok123");
        assertThat(out).doesNotContain(hex, b64url, "4/0AbCdEf", "tok123");
    }

    @Test
    void telefonosConSeparadores() {
        String out = Redactor.redact("Llamar al +57 300 123 4567 o al 301-555-0199 (ref 3001234567)");
        assertThat(out).doesNotContain("300 123 4567", "301-555-0199", "3001234567").contains("***4567", "***0199");
    }

    @Test
    void noSobreRedactaTextoDeDiagnostico() {
        String s = "Evento #12 persistido con estado PARSEADO por $1.250.000 statusCode=200 "
                + "2026-11-22T15:00:00Z conector=gmail-principal http=401 motivo=bad_signature";
        assertThat(Redactor.redact(s)).isEqualTo(s);
    }

    @Test
    void esIdempotenteYNoExplotaConEntradasHostiles() {
        String once = Redactor.redact("password=abc token: \"x y\" juan@x.com 3001234567");
        assertThat(Redactor.redact(once)).isEqualTo(once);
        // sin retroceso catastrófico (ReDoS) con 200 000 caracteres hostiles
        for (String hostile : new String[]{"a".repeat(200_000), "=".repeat(200_000), "\"".repeat(200_000),
                "1.".repeat(100_000), "token=".repeat(30_000), "a@".repeat(100_000), " ".repeat(200_000)}) {
            org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(10),
                    () -> Redactor.redact(hostile));
        }
    }

    @Test
    void excepcionConSecretoEnElMensajeSeRedactaEnLaTraza() {
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        LoggingEvent ev = new LoggingEvent("x", ctx.getLogger("t"), Level.ERROR, "fallo",
                new IllegalStateException("refresh_token=1//0gSecretoLargo Authorization: Bearer ya29.zzz9"), null);
        String out = new RedactingThrowableConverter().convert(ev);
        assertThat(out).doesNotContain("0gSecretoLargo", "ya29.zzz9").contains("IllegalStateException");
    }
}
