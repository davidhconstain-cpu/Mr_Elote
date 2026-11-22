package com.vivanetpay.integration.android;

import java.time.Duration;

/**
 * Resultado de {@link AndroidWebhookConnector#sendTestEvent()}.
 *
 * @param ok         true si el receptor aceptó el evento de prueba (HTTP 200)
 * @param httpStatus código HTTP (0 si no hubo respuesta)
 * @param message    mensaje en español para el usuario, sin secretos
 * @param latency    tiempo de ida y vuelta
 */
public record WebhookTestResult(boolean ok, int httpStatus, String message, Duration latency) {
}
