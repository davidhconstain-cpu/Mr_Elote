package com.vivanetpay.integration.android;

import java.time.Instant;
import java.util.Map;

/**
 * Contadores del receptor para la pantalla Conectores (sin datos sensibles).
 *
 * @param accepted     eventos nuevos entregados al núcleo
 * @param duplicates   reenvíos reconocidos como duplicados (respuesta 200 sin crear movimiento)
 * @param tests        eventos de prueba recibidos (no crean pagos)
 * @param rejected     rechazos por motivo (p. ej. {@code bad_signature=3})
 * @param lastAcceptedAt último evento aceptado
 * @param lastTestAt     último evento de prueba
 * @param lastRejectedAt último rechazo
 */
public record WebhookStats(long accepted, long duplicates, long tests, Map<String, Long> rejected,
                           Instant lastAcceptedAt, Instant lastTestAt, Instant lastRejectedAt) {
    public WebhookStats {
        rejected = Map.copyOf(rejected);
    }

    public long totalRejected() {
        return rejected.values().stream().mapToLong(Long::longValue).sum();
    }
}
