/**
 * Validación, normalización de importes, idempotencia/deduplicación y política de confirmación
 * (RF-009, RF-010, RF-011, RN-003..RN-007).
 * <p>
 * Contratos: {@link com.vivanetpay.validation.ValidationPolicy} (por defecto
 * {@link com.vivanetpay.validation.ConservativeValidationPolicy}, PROVISIONAL hasta DP-01),
 * {@link com.vivanetpay.validation.IdempotencyEngine}, {@link com.vivanetpay.validation.AmountNormalizer}.
 * Implementado por: agente núcleo.
 */
package com.vivanetpay.validation;
