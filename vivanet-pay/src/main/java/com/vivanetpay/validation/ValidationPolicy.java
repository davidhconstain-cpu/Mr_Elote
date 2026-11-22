package com.vivanetpay.validation;

/**
 * Política de validación: decide el estado final de un evento automático (VALIDADO_POR_FUENTE vs
 * PARSEADO/PENDIENTE_REVISION vs RECHAZADO) y si puede anunciarse como "pago confirmado" (RN-003, RF-011).
 * <p>
 * IMPORTANTE: la evidencia que permite decir "pago confirmado" es la decisión pendiente <b>DP-01</b>.
 * Mientras no se apruebe, se usa {@link ConservativeValidationPolicy}: ningún evento automático queda
 * VALIDADO_POR_FUENTE; solo la validación manual (Flujo D, {@code ReviewService}) confirma un pago.
 * Las implementaciones deben ser puras (sin E/S) y seguras para hilos.
 */
public interface ValidationPolicy {

    /** Identificador de la política (se registra en auditoría). */
    String id();

    /** Decide el estado final y el anuncio de un evento no duplicado. */
    PolicyDecision decide(PolicyInput input);

    /** Configuración efectiva. */
    ValidationPolicyConfig config();
}
