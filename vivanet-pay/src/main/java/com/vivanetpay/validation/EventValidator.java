package com.vivanetpay.validation;

import com.vivanetpay.domain.CopAmount;
import com.vivanetpay.domain.RawEvent;
import com.vivanetpay.parsing.ParseResult;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Reglas automáticas de aceptación (Flujo B paso 13): formato reconocido, importe presente y positivo,
 * moneda COP y coherencia de fecha. No decide el estado final: eso lo hace {@link ValidationPolicy}.
 */
public final class EventValidator {
    public static final String RULE_FORMAT = "FORMATO_RECONOCIDO";
    public static final String RULE_AMOUNT_PRESENT = "IMPORTE_PRESENTE";
    public static final String RULE_AMOUNT_POSITIVE = "IMPORTE_POSITIVO";
    public static final String RULE_CURRENCY = "MONEDA_COP";
    public static final String RULE_DATE = "FECHA_COHERENTE";

    /** Tolerancia de reloj para timestamps de origen en el futuro. */
    private static final Duration FUTURE_TOLERANCE = Duration.ofMinutes(5);

    public List<RuleCheck> check(RawEvent raw, ParseResult parse, Instant now) {
        List<RuleCheck> checks = new ArrayList<>();
        if (parse.recognized()) {
            checks.add(RuleCheck.pass(RULE_FORMAT, "Formato reconocido por " + parse.parserId()));
        } else {
            checks.add(RuleCheck.warn(RULE_FORMAT, parse.reason() != null ? parse.reason() : "Formato no reconocido"));
        }
        if (parse.recognized()) {
            if (parse.amount() == null) {
                checks.add(RuleCheck.warn(RULE_AMOUNT_PRESENT, "No se pudo extraer el importe"));
            } else if (!CopAmount.isValid(parse.amount())) {
                checks.add(RuleCheck.fail(RULE_AMOUNT_POSITIVE,
                        "Importe inválido: " + parse.amount() + " (debe ser mayor que cero y razonable)"));
            } else {
                checks.add(RuleCheck.pass(RULE_AMOUNT_POSITIVE, "Importe " + CopAmount.format(parse.amount())));
            }
            if (CopAmount.isSupportedCurrency(parse.currency())) {
                checks.add(RuleCheck.pass(RULE_CURRENCY, "Moneda COP"));
            } else {
                checks.add(RuleCheck.fail(RULE_CURRENCY, "Moneda no admitida (RN-006): " + parse.currency()));
            }
        }
        Instant ts = parse.occurredAt() != null ? parse.occurredAt() : raw.sourceTimestamp();
        if (ts != null && ts.isAfter(now.plus(FUTURE_TOLERANCE))) {
            checks.add(RuleCheck.warn(RULE_DATE, "La fecha de origen está en el futuro: " + ts));
        }
        return checks;
    }
}
