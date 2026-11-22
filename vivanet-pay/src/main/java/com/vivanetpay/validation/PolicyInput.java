package com.vivanetpay.validation;

import com.vivanetpay.domain.RawEvent;
import com.vivanetpay.parsing.ParseResult;

import java.util.List;
import java.util.Objects;

/**
 * Entrada para {@link ValidationPolicy#decide(PolicyInput)}.
 *
 * @param raw         evento de origen
 * @param parse       resultado del parser
 * @param checks      reglas automáticas evaluadas
 * @param idempotency decisión de idempotencia (nunca DUPLICADO: los duplicados no llegan a la política)
 */
public record PolicyInput(RawEvent raw, ParseResult parse, List<RuleCheck> checks, IdempotencyDecision idempotency) {
    public PolicyInput {
        Objects.requireNonNull(raw, "raw");
        Objects.requireNonNull(parse, "parse");
        Objects.requireNonNull(idempotency, "idempotency");
        checks = List.copyOf(checks);
    }
}
