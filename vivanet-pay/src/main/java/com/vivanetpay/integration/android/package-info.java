/**
 * Conector Android/MacroDroid (Flujo C, RF-022, RNF-003, CP-007..CP-009): receptor HTTPS autenticado
 * (HMAC + timestamp + nonce, límite de tamaño y tasa) y adaptador del payload al contrato
 * {@link com.vivanetpay.domain.RawEvent} con {@code source=ANDROID_NEQUI}.
 * <p>
 * Lo implementará el AGENTE ANDROID. Ubicación del receptor PENDIENTE (DP-03/DP-04): no exponer puertos a Internet.
 * Solicitudes inválidas se rechazan y auditan sin almacenar pago (CP-008).
 */
package com.vivanetpay.integration.android;
