package com.vivanetpay.validation;

import com.vivanetpay.domain.EventSource;

import java.util.Set;

/**
 * Configuración de la política de validación. Los valores por defecto son CONSERVADORES y PROVISIONALES
 * hasta que se apruebe DP-01 (evidencia de confirmación) y DP-09 (contenido del anuncio).
 *
 * @param automaticValidationEnabled  si true, eventos de fuentes en {@code trustedSources} que pasen todas las reglas
 *                                    con confianza suficiente quedan VALIDADO_POR_FUENTE. Por defecto TRUE desde la decisión DP-01 (conservador: false).
 * @param trustedSources              fuentes que podrían validarse automáticamente. Por defecto ANDROID_NEQUI y GMAIL.
 * @param minConfidence               confianza mínima del parser; por debajo => PENDIENTE_REVISION. Defecto 0.9.
 * @param announceUnconfirmed         anunciar "posible pago" para eventos no confirmados (RN-003). Defecto true.
 * @param announceOnManualValidation  anunciar "pago confirmado" cuando el usuario valida manualmente. Defecto false.
 */
public record ValidationPolicyConfig(boolean automaticValidationEnabled,
                                     Set<EventSource> trustedSources,
                                     double minConfidence,
                                     boolean announceUnconfirmed,
                                     boolean announceOnManualValidation) {
    public ValidationPolicyConfig {
        trustedSources = trustedSources == null ? Set.of() : Set.copyOf(trustedSources);
        if (minConfidence < 0 || minConfidence > 1) {
            throw new IllegalArgumentException("minConfidence fuera de rango");
        }
    }

    /**
     * Valores por defecto vigentes (DP-01 decidida): confirmación automática de fuentes verificadas
     * ({@link com.vivanetpay.domain.EventSource#ANDROID_NEQUI} y {@link com.vivanetpay.domain.EventSource#GMAIL})
     * bajo las reglas de {@link TrustedSourceValidationPolicy}.
     */
    public static ValidationPolicyConfig defaults() {
        return new ValidationPolicyConfig(true, Set.of(EventSource.ANDROID_NEQUI, EventSource.GMAIL), 0.9, true, false);
    }

    /** Modo conservador (anterior): nada se confirma automáticamente; solo la validación manual. */
    public static ValidationPolicyConfig conservativeDefaults() {
        return new ValidationPolicyConfig(false, Set.of(), 0.9, true, false);
    }
}
