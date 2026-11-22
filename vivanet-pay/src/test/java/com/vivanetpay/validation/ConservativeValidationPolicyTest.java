package com.vivanetpay.validation;

import com.vivanetpay.domain.AnnouncementKind;
import com.vivanetpay.domain.EventSource;
import com.vivanetpay.domain.EventStatus;
import com.vivanetpay.domain.RawEvent;
import com.vivanetpay.parsing.ParseResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ConservativeValidationPolicyTest {
    private final Instant now = Instant.parse("2026-11-22T15:00:00Z");
    private final RawEvent raw = RawEvent.builder(EventSource.ANDROID_NEQUI, "a").rawText("x").receivedAt(now).build();
    private final IdempotencyDecision nuevo = new IdempotencyDecision(IdempotencyDecision.Outcome.NUEVO, "k", "f", null, null);

    private PolicyDecision decide(ValidationPolicy p, ParseResult parse, IdempotencyDecision idem) {
        return p.decide(new PolicyInput(raw, parse, new EventValidator().check(raw, parse, now), idem));
    }

    private ParseResult perfect() {
        return ParseResult.builder("p").amount(50_000L).bank("Nequi").confidence(1.0).build();
    }

    @Test
    void porDefectoNadaAutomaticoQuedaConfirmado() {
        PolicyDecision d = decide(new ConservativeValidationPolicy(), perfect(), nuevo);
        assertThat(d.status()).isEqualTo(EventStatus.PARSEADO);
        assertThat(d.status().isConfirmed()).isFalse();
        assertThat(d.announcement()).isEqualTo(AnnouncementKind.POSIBLE_PAGO);
        assertThat(d.reason()).contains("DP-01");
    }

    @Test
    void noReconocidoVaARevision() {
        PolicyDecision d = decide(new ConservativeValidationPolicy(), ParseResult.notRecognized("p", "desconocido"), nuevo);
        assertThat(d.status()).isEqualTo(EventStatus.PENDIENTE_REVISION);
        assertThat(d.announcement()).isNotEqualTo(AnnouncementKind.PAGO_CONFIRMADO);
    }

    @Test
    void importeCeroORechazoDeMoneda() {
        PolicyDecision cero = decide(new ConservativeValidationPolicy(), ParseResult.builder("p").amount(0L).build(), nuevo);
        assertThat(cero.status()).isEqualTo(EventStatus.RECHAZADO);
        assertThat(cero.announcement()).isEqualTo(AnnouncementKind.NONE);
        PolicyDecision usd = decide(new ConservativeValidationPolicy(),
                ParseResult.builder("p").amount(10L).currency("USD").build(), nuevo);
        assertThat(usd.status()).isEqualTo(EventStatus.RECHAZADO);
    }

    @Test
    void ambiguoYConfianzaBajaVanARevision() {
        var amb = new IdempotencyDecision(IdempotencyDecision.Outcome.AMBIGUO, "k", "f", 9L, "posible duplicado #9");
        assertThat(decide(new ConservativeValidationPolicy(), perfect(), amb).status()).isEqualTo(EventStatus.PENDIENTE_REVISION);
        ParseResult low = ParseResult.builder("p").amount(1000L).confidence(0.3).build();
        assertThat(decide(new ConservativeValidationPolicy(), low, nuevo).status()).isEqualTo(EventStatus.PENDIENTE_REVISION);
    }

    @Test
    void validacionAutomaticaSoloSiSeHabilitaExplicitamente() {
        var cfg = new ValidationPolicyConfig(true, Set.of(EventSource.ANDROID_NEQUI), 0.9, true, false);
        PolicyDecision d = decide(new ConservativeValidationPolicy(cfg), perfect(), nuevo);
        assertThat(d.status()).isEqualTo(EventStatus.VALIDADO_POR_FUENTE);
        assertThat(d.announcement()).isEqualTo(AnnouncementKind.PAGO_CONFIRMADO);
        var otherSource = new ValidationPolicyConfig(true, Set.of(EventSource.GMAIL), 0.9, true, false);
        assertThat(decide(new ConservativeValidationPolicy(otherSource), perfect(), nuevo).status()).isEqualTo(EventStatus.PARSEADO);
    }

    @Test
    void sinAnunciosNoConfirmadosSiSeDesactiva() {
        var cfg = new ValidationPolicyConfig(false, Set.of(), 0.9, false, false);
        assertThat(decide(new ConservativeValidationPolicy(cfg), perfect(), nuevo).announcement()).isEqualTo(AnnouncementKind.NONE);
        assertThat(List.of(EventStatus.values())).isNotEmpty();
    }
}
