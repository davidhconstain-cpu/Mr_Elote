package com.vivanetpay.integration.android;

import java.time.Instant;

/**
 * Cuerpo JSON que envía el teléfono (schemaVersion 1). Ver {@link WebhookPayloadParser} para las reglas y
 * {@code tools/macrodroid/payload-template.json} para la plantilla.
 *
 * @param schemaVersion versión del contrato (solo 1)
 * @param eventId       identificador único y estable del evento en el teléfono (clave de idempotencia)
 * @param source        siempre {@code ANDROID_NEQUI} en el MVP
 * @param app           paquete/nombre de la app que notificó (opcional)
 * @param title         título de la notificación (puede ser vacío)
 * @param text          texto de la notificación (obligatorio)
 * @param capturedAt    instante de captura en el teléfono
 * @param device        nombre del teléfono (opcional)
 * @param test          true = evento de prueba: se autentica y valida pero NO se envía al núcleo (no crea pagos)
 */
public record WebhookPayload(int schemaVersion, String eventId, String source, String app, String title,
                             String text, Instant capturedAt, String device, boolean test) {
}
