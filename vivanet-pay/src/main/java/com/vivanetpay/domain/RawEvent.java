package com.vivanetpay.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Contrato común de entrada (RF-005, Anexo B): lo que TODO conector entrega al núcleo mediante
 * {@link com.vivanetpay.application.EventSink#ingest(RawEvent)}.
 * <p>
 * Contiene solo el contenido de origen permitido; el núcleo se encarga de analizar, validar,
 * deduplicar y persistir. Los conectores NO deben interpretar importes ni decidir estados.
 *
 * @param externalEventId identificador único del origen (Gmail messageId, eventId de MacroDroid).
 *                        Opcional, pero muy recomendado: es la primera clave de idempotencia (RN-004).
 * @param source          fuente del evento
 * @param connectorId     identificador del conector que lo capturó (p. ej. "gmail-principal")
 * @param sourceTimestamp marca temporal del origen en UTC (fecha del correo, captura de la notificación); nullable
 * @param receivedAt      instante UTC en que el conector lo recibió
 * @param subject         asunto del correo o título de la notificación; nullable
 * @param sender          remitente técnico (cabecera From o paquete Android); nullable
 * @param rawText         texto de origen permitido (cuerpo en texto plano o texto de la notificación).
 *                        Máximo {@link #MAX_RAW_TEXT_LENGTH} caracteres.
 * @param headers         metadatos de origen relevantes (cabeceras de correo, campos del webhook). No incluir secretos.
 * @param schemaVersion   versión del contrato (actual: {@link #CURRENT_SCHEMA_VERSION})
 * @param correlationId   identificador de correlación para logs (IDD §10.3); se genera si es null
 * @param historical      true si proviene de una sincronización inicial/de recuperación y NO debe anunciarse
 *                        por voz (IDD §9.2: no reproducir anuncios históricos)
 */
public record RawEvent(String externalEventId,
                       EventSource source,
                       String connectorId,
                       Instant sourceTimestamp,
                       Instant receivedAt,
                       String subject,
                       String sender,
                       String rawText,
                       Map<String, String> headers,
                       int schemaVersion,
                       String correlationId,
                       boolean historical) {

    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final int MAX_RAW_TEXT_LENGTH = 65_536;
    public static final int MAX_FIELD_LENGTH = 1_024;
    public static final int MAX_HEADERS = 64;

    public RawEvent {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(receivedAt, "receivedAt");
        if (connectorId == null || connectorId.isBlank()) {
            throw new IllegalArgumentException("connectorId es obligatorio");
        }
        rawText = rawText == null ? "" : rawText;
        if (rawText.length() > MAX_RAW_TEXT_LENGTH) {
            throw new IllegalArgumentException("rawText excede " + MAX_RAW_TEXT_LENGTH + " caracteres");
        }
        checkLength("externalEventId", externalEventId);
        checkLength("subject", subject);
        checkLength("sender", sender);
        externalEventId = blankToNull(externalEventId);
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        if (headers.size() > MAX_HEADERS) {
            throw new IllegalArgumentException("Demasiados headers: " + headers.size());
        }
        if (schemaVersion <= 0) {
            schemaVersion = CURRENT_SCHEMA_VERSION;
        }
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
    }

    private static void checkLength(String name, String value) {
        if (value != null && value.length() > MAX_FIELD_LENGTH) {
            throw new IllegalArgumentException(name + " excede " + MAX_FIELD_LENGTH + " caracteres");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** Constructor fluido para conectores y pruebas. */
    public static Builder builder(EventSource source, String connectorId) {
        return new Builder(source, connectorId);
    }

    /** Builder de {@link RawEvent}. */
    public static final class Builder {
        private final EventSource source;
        private final String connectorId;
        private String externalEventId;
        private Instant sourceTimestamp;
        private Instant receivedAt;
        private String subject;
        private String sender;
        private String rawText;
        private Map<String, String> headers = Map.of();
        private int schemaVersion = CURRENT_SCHEMA_VERSION;
        private String correlationId;
        private boolean historical;

        private Builder(EventSource source, String connectorId) {
            this.source = source;
            this.connectorId = connectorId;
        }

        public Builder externalEventId(String v) { this.externalEventId = v; return this; }
        public Builder sourceTimestamp(Instant v) { this.sourceTimestamp = v; return this; }
        public Builder receivedAt(Instant v) { this.receivedAt = v; return this; }
        public Builder subject(String v) { this.subject = v; return this; }
        public Builder sender(String v) { this.sender = v; return this; }
        public Builder rawText(String v) { this.rawText = v; return this; }
        public Builder headers(Map<String, String> v) { this.headers = v; return this; }
        public Builder schemaVersion(int v) { this.schemaVersion = v; return this; }
        public Builder correlationId(String v) { this.correlationId = v; return this; }
        public Builder historical(boolean v) { this.historical = v; return this; }

        public RawEvent build() {
            return new RawEvent(externalEventId, source, connectorId, sourceTimestamp,
                    receivedAt == null ? Instant.now() : receivedAt, subject, sender, rawText,
                    headers, schemaVersion, correlationId, historical);
        }
    }
}
