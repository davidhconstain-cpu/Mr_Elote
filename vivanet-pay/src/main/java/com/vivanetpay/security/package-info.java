/**
 * Seguridad: almacén de secretos, firmas y permisos (RNF-001..RNF-003, IDD §8.3).
 * <p>
 * Contrato: {@link com.vivanetpay.security.SecretStore}. El núcleo solo trae
 * {@link com.vivanetpay.security.InMemorySecretStore} (pruebas). El AGENTE DE SEGURIDAD/GMAIL implementará
 * el almacén de Windows (Credential Manager / DPAPI) y la verificación HMAC/nonce del webhook.
 * Nunca escribir secretos en código, logs, {@code app_setting} ni el repositorio.
 */
package com.vivanetpay.security;
