package com.vivanetpay.parsing;

import com.vivanetpay.domain.RawEvent;

/**
 * Parser de último recurso: acepta cualquier evento y lo marca como NO reconocido, de modo que
 * quede PENDIENTE_REVISION (RF-007, CP-006). Nunca extrae importes.
 */
public final class UnrecognizedFallbackParser implements PaymentParser {
    public static final String ID = "fallback-no-reconocido-v1";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public int priority() {
        return Integer.MIN_VALUE;
    }

    @Override
    public boolean supports(RawEvent event) {
        return true;
    }

    @Override
    public ParseResult parse(RawEvent event) {
        return ParseResult.notRecognized(ID, "Formato de notificación no reconocido; requiere revisión manual");
    }
}
