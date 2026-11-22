package com.vivanetpay.parsing;

import java.time.Instant;
import java.util.Optional;

/**
 * Resultado de análisis de un {@link com.vivanetpay.domain.RawEvent}. Todos los campos extraídos son opcionales.
 *
 * @param parserId    parser que produjo el resultado
 * @param recognized  true si la estructura fue reconocida (plantilla conocida)
 * @param amount      importe en pesos enteros (nullable)
 * @param currency    moneda detectada (nullable = no explícita; se asume COP por contexto)
 * @param bank        entidad normalizada, p. ej. "Nequi" (nullable)
 * @param sender      nombre de quien envía el pago (nullable)
 * @param reference   referencia/comprobante (nullable)
 * @param occurredAt  fecha/hora del pago según el texto (nullable)
 * @param confidence  confianza 0.0..1.0 en la extracción
 * @param reason      motivo cuando el resultado no es concluyente (nullable si es concluyente)
 */
public record ParseResult(String parserId,
                          boolean recognized,
                          Long amount,
                          String currency,
                          String bank,
                          String sender,
                          String reference,
                          Instant occurredAt,
                          double confidence,
                          String reason) {

    public ParseResult {
        if (parserId == null || parserId.isBlank()) {
            throw new IllegalArgumentException("parserId es obligatorio");
        }
        if (Double.isNaN(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence debe estar entre 0 y 1: " + confidence);
        }
        bank = blankToNull(bank);
        sender = blankToNull(sender);
        reference = blankToNull(reference);
        currency = blankToNull(currency);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** Resultado no reconocido con motivo. */
    public static ParseResult notRecognized(String parserId, String reason) {
        return new ParseResult(parserId, false, null, null, null, null, null, null, 0.0, reason);
    }

    public Optional<Long> amountOpt() { return Optional.ofNullable(amount); }
    public Optional<String> bankOpt() { return Optional.ofNullable(bank); }
    public Optional<String> senderOpt() { return Optional.ofNullable(sender); }
    public Optional<String> referenceOpt() { return Optional.ofNullable(reference); }
    public Optional<Instant> occurredAtOpt() { return Optional.ofNullable(occurredAt); }

    /** @return true si se reconoció y hay importe: requisito mínimo para crear un movimiento. */
    public boolean hasAmount() {
        return recognized && amount != null;
    }

    public static Builder builder(String parserId) {
        return new Builder(parserId);
    }

    /** Builder para resultados reconocidos. */
    public static final class Builder {
        private final String parserId;
        private boolean recognized = true;
        private Long amount;
        private String currency;
        private String bank;
        private String sender;
        private String reference;
        private Instant occurredAt;
        private double confidence = 1.0;
        private String reason;

        private Builder(String parserId) {
            this.parserId = parserId;
        }

        public Builder recognized(boolean v) { this.recognized = v; return this; }
        public Builder amount(Long v) { this.amount = v; return this; }
        public Builder currency(String v) { this.currency = v; return this; }
        public Builder bank(String v) { this.bank = v; return this; }
        public Builder sender(String v) { this.sender = v; return this; }
        public Builder reference(String v) { this.reference = v; return this; }
        public Builder occurredAt(Instant v) { this.occurredAt = v; return this; }
        public Builder confidence(double v) { this.confidence = v; return this; }
        public Builder reason(String v) { this.reason = v; return this; }

        public ParseResult build() {
            return new ParseResult(parserId, recognized, amount, currency, bank, sender, reference,
                    occurredAt, confidence, reason);
        }
    }
}
