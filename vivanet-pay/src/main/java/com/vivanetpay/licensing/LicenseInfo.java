package com.vivanetpay.licensing;

import java.time.Instant;

/**
 * Instantánea del estado de licencia para la UI.
 *
 * @param status         estado
 * @param trialStartedAt inicio de la prueba (UTC)
 * @param trialEndsAt    fin de la prueba (UTC)
 * @param lastCheckAt    última verificación (UTC)
 * @param daysRemaining  días restantes de prueba (0 si vencida)
 * @param message        mensaje en español claro con acción recomendada
 */
public record LicenseInfo(LicenseStatus status, Instant trialStartedAt, Instant trialEndsAt, Instant lastCheckAt,
                          long daysRemaining, String message) {
}
