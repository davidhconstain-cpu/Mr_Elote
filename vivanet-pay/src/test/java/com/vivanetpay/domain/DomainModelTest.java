package com.vivanetpay.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainModelTest {

    @Test
    void transaccionExigeImportePositivoYCop() {
        Instant now = Instant.now();
        assertThatThrownBy(() -> new Transaction(null, 1L, "Nequi", 0, "COP", null, null, now, now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Transaction(null, 1L, "Nequi", -5, "COP", null, null, now, now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Transaction(null, 1L, "Nequi", 5000, "USD", null, null, now, now))
                .isInstanceOf(IllegalArgumentException.class);
        Transaction t = new Transaction(null, 1L, "Nequi", 125_000, null, null, null, now, now);
        assertThat(t.currency()).isEqualTo("COP");
        assertThat(t.formattedAmount()).isEqualTo("$125.000");
    }

    @Test
    void formatoColombiano() {
        assertThat(CopAmount.format(1_250_000)).isEqualTo("$1.250.000");
        assertThat(CopAmount.format(999)).isEqualTo("$999");
        assertThat(CopAmount.format(1000)).isEqualTo("$1.000");
    }

    @Test
    void fechaLocalBogota() {
        assertThat(AppTime.localDate(Instant.parse("2026-11-22T04:59:59Z"))).isEqualTo(LocalDate.of(2026, 11, 21));
        assertThat(AppTime.localDate(Instant.parse("2026-11-22T05:00:00Z"))).isEqualTo(LocalDate.of(2026, 11, 22));
        assertThat(AppTime.startOfDay(LocalDate.of(2026, 11, 22))).isEqualTo(Instant.parse("2026-11-22T05:00:00Z"));
    }

    @Test
    void rawEventValidaLimitesYGeneraCorrelacion() {
        RawEvent e = RawEvent.builder(EventSource.MANUAL, "manual").rawText("x").externalEventId("  ").build();
        assertThat(e.correlationId()).isNotBlank();
        assertThat(e.externalEventId()).isNull();
        assertThat(e.schemaVersion()).isEqualTo(RawEvent.CURRENT_SCHEMA_VERSION);
        assertThatThrownBy(() -> RawEvent.builder(EventSource.MANUAL, "manual")
                .rawText("a".repeat(RawEvent.MAX_RAW_TEXT_LENGTH + 1)).build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RawEvent.builder(EventSource.MANUAL, " ").build())
                .isInstanceOf(IllegalArgumentException.class);
    }
}
