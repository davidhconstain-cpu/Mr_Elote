package com.vivanetpay.integration.gmail;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MessageTextExtractorTest {

    @Test
    void prefersPlainTextInMultipartAlternativeAndKeepsAccents() {
        MessagePart root = MessagePart.multipart("multipart/alternative", List.of(
                MessagePart.text("text/plain", "UTF-8", "Recibiste $25.000 de José Peña".getBytes(StandardCharsets.UTF_8)),
                MessagePart.text("text/html", "UTF-8", "<p>otro</p>".getBytes(StandardCharsets.UTF_8))));
        assertThat(MessageTextExtractor.extract(root)).isEqualTo("Recibiste $25.000 de José Peña");
    }

    @Test
    void convertsHtmlWithEntitiesAndTablesToSimpleText() {
        String html = "<html><head><style>p{color:red}</style></head><body><p>Hola&nbsp;Mar&iacute;a,</p>"
                + "<table><tr><td>Valor</td><td>&#36;50.000</td></tr><tr><td>Origen</td><td>Se&ntilde;or A&ntilde;ez</td></tr></table>"
                + "<br>Gracias&#x21;<script>alert(1)</script></body></html>";
        MessagePart root = MessagePart.multipart("multipart/mixed", List.of(
                MessagePart.text("text/html", "UTF-8", html.getBytes(StandardCharsets.UTF_8))));
        String text = MessageTextExtractor.extract(root);
        assertThat(text).contains("Hola María,").contains("Valor $50.000").contains("Origen Señor Añez")
                .contains("Gracias!").doesNotContain("alert").doesNotContain("color:red").doesNotContain("<");
    }

    @Test
    void honoursLatin1CharsetAndSkipsAttachments() {
        MessagePart latin = MessagePart.text("text/plain", "ISO-8859-1", "Transferencia de Raúl Muñoz".getBytes(StandardCharsets.ISO_8859_1));
        MessagePart attachment = new MessagePart("text/plain", "extracto.txt", java.util.Map.of(), "SECRETO".getBytes(), List.of());
        String text = MessageTextExtractor.extract(MessagePart.multipart("multipart/mixed", List.of(latin, attachment)));
        assertThat(text).isEqualTo("Transferencia de Raúl Muñoz");
    }

    @Test
    void decodesRfc2047Headers() {
        assertThat(MessageTextExtractor.decodeHeader("=?UTF-8?B?wqFSZWNpYmlzdGUgcGxhdGEh?=")).isEqualTo("¡Recibiste plata!");
        assertThat(MessageTextExtractor.decodeHeader("=?ISO-8859-1?Q?Transferencia_recibida_Jos=E9?=")).isEqualTo("Transferencia recibida José");
        assertThat(MessageTextExtractor.decodeHeader("Asunto simple")).isEqualTo("Asunto simple");
    }

    @Test
    void nullAndEmptyAreSafe() {
        assertThat(MessageTextExtractor.extract(null)).isEmpty();
        assertThat(MessageTextExtractor.extract(MessagePart.multipart("multipart/mixed", List.of()))).isEmpty();
    }
}
