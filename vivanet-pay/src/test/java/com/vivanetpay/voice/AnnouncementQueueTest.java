package com.vivanetpay.voice;

import com.vivanetpay.domain.AnnouncementKind;
import com.vivanetpay.domain.AnnouncementStatus;
import com.vivanetpay.domain.EventStatus;
import com.vivanetpay.support.MutableClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnnouncementQueueTest {
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-22T15:00:00Z"));

    private AnnouncementRequest posible(long id, long amount) {
        return new AnnouncementRequest(id, id, AnnouncementKind.POSIBLE_PAGO, EventStatus.PARSEADO, "Nequi", amount,
                "Ana", clock.instant(), false, null, null);
    }

    @Test
    void reproduceDeUnoEnUnoSinSolapamientos() throws Exception {
        LoggingSpeechEngine engine = new LoggingSpeechEngine(20);
        try (AnnouncementQueue q = new AnnouncementQueue(engine, VoiceSettings.defaults(), clock, Duration.ofMinutes(15))) {
            Thread[] threads = new Thread[5];
            for (int i = 0; i < threads.length; i++) {
                long n = i;
                threads[i] = new Thread(() -> q.enqueue(posible(n, 1000 * (n + 1))));
                threads[i].start();
            }
            for (Thread t : threads) {
                t.join();
            }
            assertThat(q.awaitIdle(Duration.ofSeconds(5))).isTrue();
            assertThat(engine.spoken()).hasSize(5);
            assertThat(engine.maxConcurrent()).isEqualTo(1);
        }
    }

    @Test
    void noAnunciaDuplicadosHistoricosAntiguosNiSilenciado() throws Exception {
        LoggingSpeechEngine engine = new LoggingSpeechEngine();
        List<AnnouncementStatus> outcomes = new CopyOnWriteArrayList<>();
        try (AnnouncementQueue q = new AnnouncementQueue(engine, VoiceSettings.defaults(), clock, Duration.ofMinutes(15))) {
            q.addOutcomeListener((r, m, s, qa, fa, e) -> outcomes.add(s));
            assertThat(q.enqueue(new AnnouncementRequest(1L, null, AnnouncementKind.POSIBLE_PAGO, EventStatus.DUPLICADO,
                    "Nequi", 1L, null, clock.instant(), false, null, null))).isFalse();
            assertThat(q.enqueue(new AnnouncementRequest(2L, null, AnnouncementKind.POSIBLE_PAGO, EventStatus.PARSEADO,
                    "Nequi", 1L, null, clock.instant(), true, null, null))).isFalse();
            assertThat(q.enqueue(new AnnouncementRequest(3L, null, AnnouncementKind.POSIBLE_PAGO, EventStatus.PARSEADO,
                    "Nequi", 1L, null, clock.instant().minus(Duration.ofHours(2)), false, null, null))).isFalse();
            q.setMuted(true);
            assertThat(q.enqueue(posible(4, 1000))).isFalse();
            q.setMuted(false);
            assertThat(q.enqueue(posible(5, 1000))).isTrue();
            q.awaitIdle(Duration.ofSeconds(5));
            assertThat(engine.spoken()).hasSize(1);
            assertThat(outcomes).containsExactly(AnnouncementStatus.OMITIDO, AnnouncementStatus.OMITIDO,
                    AnnouncementStatus.OMITIDO, AnnouncementStatus.REPRODUCIDO);
        }
    }

    @Test
    void falloDeAudioSeRegistra() throws Exception {
        LoggingSpeechEngine engine = new LoggingSpeechEngine();
        engine.failNext();
        List<AnnouncementStatus> outcomes = new CopyOnWriteArrayList<>();
        try (AnnouncementQueue q = new AnnouncementQueue(engine, VoiceSettings.defaults(), clock, Duration.ofMinutes(15))) {
            q.addOutcomeListener((r, m, s, qa, fa, e) -> outcomes.add(s));
            q.enqueue(posible(1, 1000));
            q.awaitIdle(Duration.ofSeconds(5));
            assertThat(outcomes).containsExactly(AnnouncementStatus.FALLIDO);
        }
    }

    @Test
    void mensajesSegunIdd93() {
        VoiceSettings s = VoiceSettings.defaults();
        assertThat(AnnouncementMessages.build(posible(1, 125_000), s))
                .isEqualTo("Nueva notificación de Nequi por 125.000 pesos. Verifica el pago.");
        var confirmed = new AnnouncementRequest(1L, 1L, AnnouncementKind.PAGO_CONFIRMADO, EventStatus.VALIDADO_POR_FUENTE,
                "Nequi", 125_000L, "Ana", null, false, null, null);
        assertThat(AnnouncementMessages.build(confirmed, s)).isEqualTo("Pago recibido por 125.000 pesos desde Nequi.");
        assertThat(AnnouncementMessages.build(confirmed, new VoiceSettings(true, 80, 0, null, true))).contains("de Ana");
        assertThat(AnnouncementMessages.build(AnnouncementRequest.connectorAlert("Gmail"), s))
                .isEqualTo("Atención: la conexión de Gmail está desconectada.");
    }

    @Test
    void pagoConfirmadoExigeEstadoConfirmadoRN003() {
        assertThatThrownBy(() -> new AnnouncementRequest(1L, 1L, AnnouncementKind.PAGO_CONFIRMADO, EventStatus.PARSEADO,
                "Nequi", 1L, null, null, false, null, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
