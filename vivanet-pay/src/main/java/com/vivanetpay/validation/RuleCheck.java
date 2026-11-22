package com.vivanetpay.validation;

import com.vivanetpay.domain.ValidationResult;

import java.util.Objects;

/**
 * Resultado de una regla automática. Se persiste como fila de {@code validation}.
 *
 * @param rule   código de regla
 * @param result APROBADO / FALLIDO (rechaza) / ADVERTENCIA (revisión)
 * @param reason motivo en español
 */
public record RuleCheck(String rule, ValidationResult result, String reason) {
    public RuleCheck {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(result, "result");
    }

    public static RuleCheck pass(String rule, String reason) {
        return new RuleCheck(rule, ValidationResult.APROBADO, reason);
    }

    public static RuleCheck fail(String rule, String reason) {
        return new RuleCheck(rule, ValidationResult.FALLIDO, reason);
    }

    public static RuleCheck warn(String rule, String reason) {
        return new RuleCheck(rule, ValidationResult.ADVERTENCIA, reason);
    }
}
