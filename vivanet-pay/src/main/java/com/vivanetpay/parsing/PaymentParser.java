package com.vivanetpay.parsing;

import com.vivanetpay.domain.RawEvent;

/**
 * Analizador de una familia de notificaciones (p. ej. "push Nequi v1", "correo Bancolombia v2").
 * <p>
 * Reglas del contrato:
 * <ul>
 *   <li>Debe ser sin estado y seguro para hilos.</li>
 *   <li>{@link #supports(RawEvent)} debe ser barato (fuente, remitente, palabras clave).</li>
 *   <li>{@link #parse(RawEvent)} NO debe lanzar excepciones por formatos inesperados: debe devolver
 *       {@link ParseResult#notRecognized(String, String)} con el motivo. Si lanza, el registro lo trata
 *       como no reconocido.</li>
 *   <li>Nunca decide el estado final ni marca "confirmado": eso es de {@code ValidationPolicy}.</li>
 *   <li>Los importes se devuelven como pesos enteros ({@code long}); usar
 *       {@link com.vivanetpay.validation.AmountNormalizer}.</li>
 * </ul>
 */
public interface PaymentParser {

    /** Identificador versionado, p. ej. "nequi-android-v1". Se persiste en {@code payment_event.parser_id}. */
    String id();

    /** Prioridad: mayor valor se evalúa primero. Por defecto 0. */
    default int priority() {
        return 0;
    }

    /** @return true si este parser reconoce la familia de este evento. */
    boolean supports(RawEvent event);

    /** Extrae los campos del evento. */
    ParseResult parse(RawEvent event);
}
