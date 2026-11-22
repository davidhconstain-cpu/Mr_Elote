package com.vivanetpay.integration.gmail;

import java.io.IOException;

/** Clasificación de errores HTTP/OAuth de Google en las excepciones del conector. Sin secretos en mensajes. */
final class GmailErrors {

    private GmailErrors() {
    }

    /**
     * @param status     código HTTP (0 si no aplica)
     * @param reason     motivo de error de la API (p. ej. "rateLimitExceeded") o null
     * @param oauthError error OAuth (p. ej. "invalid_grant") o null
     * @param history    true si la llamada era history.list (404 = historyId expirado)
     */
    static IOException classify(int status, String reason, String oauthError, boolean history, IOException original) {
        if ("invalid_grant".equals(oauthError) || "invalid_token".equals(oauthError)
                || "unauthorized_client".equals(oauthError)) {
            return new GmailAuthException("Google ya no acepta la autorización de Gmail (revocada o vencida). "
                    + "Vuelve a autorizar la cuenta.", original);
        }
        if (status == 401) {
            return new GmailAuthException("La sesión de Gmail venció. Vuelve a autorizar la cuenta.", original);
        }
        if (status == 429 || (status == 403 && reason != null && reason.toLowerCase(java.util.Locale.ROOT).contains("ratelimit"))
                || (status == 403 && "quotaExceeded".equals(reason))) {
            return new GmailRateLimitException("Gmail limitó temporalmente las consultas");
        }
        if (status == 403) {
            return new GmailAuthException("Gmail rechazó el permiso solicitado. Revisa que la API de Gmail esté "
                    + "habilitada y vuelve a autorizar la cuenta.", original);
        }
        if (status == 404 && history) {
            return new GmailHistoryExpiredException("historyId expirado");
        }
        return original;
    }
}
