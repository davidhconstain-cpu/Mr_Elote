package com.vivanetpay.validation;

import java.time.Duration;
import java.util.Objects;

/**
 * Motor de idempotencia (RN-004, RN-005, RF-010, RNF-005, CP-009, CP-014).
 * <ol>
 *   <li>Si hay identificador externo: clave {@code ext:FUENTE:id}. Coincidencia exacta => DUPLICADO.</li>
 *   <li>Si no: clave {@code fp:huella} con la huella SHA-256 de campos normalizados. Coincidencia => DUPLICADO.</li>
 *   <li>Coincidencia parcial (mismo importe, remitente compatible, ventana corta) con un evento de OTRA fuente,
 *       o sin identificador externo en alguno de los dos => AMBIGUO (va a PENDIENTE_REVISION con motivo).</li>
 * </ol>
 * La garantía final ante carreras la da la restricción UNIQUE de {@code payment_event.idempotency_key}
 * en la base de datos; el servicio de ingesta reintenta la decisión si la inserción viola la restricción.
 */
public final class IdempotencyEngine {
    /** Ventana por defecto para coincidencias parciales. */
    public static final Duration DEFAULT_SIMILARITY_WINDOW = Duration.ofMinutes(10);

    private final IdempotencyLookup lookup;
    private final Duration similarityWindow;

    public IdempotencyEngine(IdempotencyLookup lookup) {
        this(lookup, DEFAULT_SIMILARITY_WINDOW);
    }

    public IdempotencyEngine(IdempotencyLookup lookup, Duration similarityWindow) {
        this.lookup = Objects.requireNonNull(lookup, "lookup");
        this.similarityWindow = Objects.requireNonNull(similarityWindow, "similarityWindow");
    }

    /** Clave de idempotencia de un candidato. */
    public static String idempotencyKey(EventCandidate c, String fingerprint) {
        if (c.externalId() != null && !c.externalId().isBlank()) {
            return "ext:" + c.source().name() + ":" + c.externalId().trim();
        }
        return "fp:" + fingerprint;
    }

    public IdempotencyDecision evaluate(EventCandidate c) {
        String fp = Fingerprint.of(c);
        String key = idempotencyKey(c, fp);

        var byKey = lookup.findByIdempotencyKey(key);
        if (byKey.isPresent()) {
            String why = key.startsWith("ext:")
                    ? "Mismo identificador externo que el evento #" + byKey.get()
                    : "Misma huella de contenido que el evento #" + byKey.get();
            return new IdempotencyDecision(IdempotencyDecision.Outcome.DUPLICADO, key, fp, byKey.get(), why);
        }
        if (c.externalId() == null) {
            var byFp = lookup.findByFingerprint(fp);
            if (byFp.isPresent()) {
                return new IdempotencyDecision(IdempotencyDecision.Outcome.DUPLICADO, key, fp, byFp.get(),
                        "Misma huella de contenido que el evento #" + byFp.get());
            }
        }
        if (c.amount() != null) {
            String sender = TextNormalizer.normalize(c.sender());
            for (var s : lookup.findSameAmountBetween(c.amount(),
                    c.occurredAt().minus(similarityWindow), c.occurredAt().plus(similarityWindow))) {
                String otherSender = TextNormalizer.normalize(s.sender());
                boolean senderCompatible = sender.isEmpty() || otherSender.isEmpty() || sender.equals(otherSender);
                if (!senderCompatible) {
                    continue;
                }
                boolean otherSource = s.source() != c.source();
                boolean missingIds = c.externalId() == null || s.externalId() == null;
                if (otherSource || missingIds) {
                    return new IdempotencyDecision(IdempotencyDecision.Outcome.AMBIGUO, key, fp, s.eventId(),
                            "Posible duplicado del evento #" + s.eventId() + " (" + s.source()
                                    + "): mismo importe y remitente compatible en ventana de "
                                    + similarityWindow.toMinutes() + " min");
                }
            }
        }
        return new IdempotencyDecision(IdempotencyDecision.Outcome.NUEVO, key, fp, null, null);
    }
}
