/**
 * Conector Android/MacroDroid (Flujo C, RF-022, RNF-003, CP-007..CP-009).
 * <ul>
 *   <li>{@link com.vivanetpay.integration.android.AndroidWebhookConnector}: receptor HTTP(S) local
 *       ({@code com.sun.net.httpserver}); por defecto solo 127.0.0.1; IP LAN explícita.</li>
 *   <li>{@link com.vivanetpay.integration.android.WebhookRequestProcessor}: autenticación (HMAC + timestamp + nonce o
 *       token Bearer), anti-replay, límites de tamaño/tasa, esquema estricto e ingesta idempotente. Es el PUNTO DE
 *       EXTENSIÓN para un futuro servicio intermedio en la nube (DP-03/DP-04 pendientes): reutilizarlo sin cambios.</li>
 *   <li>{@link com.vivanetpay.integration.android.WebhookSecretManager}: secreto de 256 bits en
 *       {@link com.vivanetpay.security.SecretStore}, con rotación y periodo de gracia.</li>
 * </ul>
 * Solicitudes inválidas se rechazan y auditan en log (sin cuerpo ni firma) sin almacenar pago (CP-008).
 * Guía de usuario: {@code docs/MACRODROID.md}; plantilla: {@code tools/macrodroid/}.
 */
package com.vivanetpay.integration.android;
