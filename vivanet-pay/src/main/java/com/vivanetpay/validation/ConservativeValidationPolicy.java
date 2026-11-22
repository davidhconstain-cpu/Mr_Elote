package com.vivanetpay.validation;

import com.vivanetpay.domain.AnnouncementKind;
import com.vivanetpay.domain.EventStatus;
import com.vivanetpay.domain.ValidationResult;

import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Política por defecto, CONSERVADORA y PROVISIONAL hasta DP-01.
 * <ul>
 *   <li>Alguna regla FALLIDA (importe ≤ 0, moneda ≠ COP) => RECHAZADO, sin anuncio (CP-010).</li>
 *   <li>Formato no reconocido, importe ausente, advertencias, coincidencia ambigua o confianza baja
 *       => PENDIENTE_REVISION, anuncio "posible pago" (CP-006, CP-014).</li>
 *   <li>Todo correcto => PARSEADO (posible pago por verificar) y anuncio "posible pago".
 *       Solo si {@code automaticValidationEnabled} y la fuente está en {@code trustedSources}
 *       => VALIDADO_POR_FUENTE con anuncio "pago confirmado". Ambos están desactivados por defecto.</li>
 * </ul>
 * Así, con la configuración por defecto ningún evento automático se presenta como "pago confirmado" (RF-011).
 */
public final class ConservativeValidationPolicy implements ValidationPolicy {
    public static final String ID = "conservadora-provisional-dp01-v1";

    private final ValidationPolicyConfig config;

    public ConservativeValidationPolicy() {
        this(ValidationPolicyConfig.conservativeDefaults());
    }

    public ConservativeValidationPolicy(ValidationPolicyConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public ValidationPolicyConfig config() {
        return config;
    }

    @Override
    public PolicyDecision decide(PolicyInput in) {
        String failures = in.checks().stream().filter(c -> c.result() == ValidationResult.FALLIDO)
                .map(RuleCheck::reason).collect(Collectors.joining("; "));
        if (!failures.isEmpty()) {
            return new PolicyDecision(EventStatus.RECHAZADO, AnnouncementKind.NONE, failures);
        }
        AnnouncementKind unconfirmed = config.announceUnconfirmed() ? AnnouncementKind.POSIBLE_PAGO : AnnouncementKind.NONE;
        if (in.idempotency().isAmbiguous()) {
            return new PolicyDecision(EventStatus.PENDIENTE_REVISION, unconfirmed, in.idempotency().reason());
        }
        String warnings = in.checks().stream().filter(c -> c.result() == ValidationResult.ADVERTENCIA)
                .map(RuleCheck::reason).collect(Collectors.joining("; "));
        if (!warnings.isEmpty()) {
            return new PolicyDecision(EventStatus.PENDIENTE_REVISION, unconfirmed, warnings);
        }
        if (!in.parse().hasAmount()) {
            return new PolicyDecision(EventStatus.PENDIENTE_REVISION, unconfirmed, "No se pudo extraer el importe");
        }
        if (in.parse().confidence() < config.minConfidence()) {
            return new PolicyDecision(EventStatus.PENDIENTE_REVISION, unconfirmed,
                    String.format("Confianza del análisis insuficiente (%.2f < %.2f)",
                            in.parse().confidence(), config.minConfidence()));
        }
        if (config.automaticValidationEnabled() && config.trustedSources().contains(in.raw().source())) {
            return new PolicyDecision(EventStatus.VALIDADO_POR_FUENTE, AnnouncementKind.PAGO_CONFIRMADO,
                    "Validado automáticamente por fuente de confianza configurada");
        }
        return new PolicyDecision(EventStatus.PARSEADO, unconfirmed,
                "Posible pago: requiere verificación manual en el canal oficial (política provisional DP-01)");
    }
}
