package com.vivanetpay.voice;

import com.vivanetpay.domain.CopAmount;

/**
 * Frases en español según IDD §9.3. "Pago recibido" solo se usa para PAGO_CONFIRMADO (RN-003).
 */
public final class AnnouncementMessages {
    private AnnouncementMessages() {
    }

    public static String build(AnnouncementRequest r, VoiceSettings settings) {
        String entity = r.bank() != null ? r.bank() : "una entidad no identificada";
        String amount = r.amount() != null ? CopAmount.group(r.amount()) + " pesos" : null;
        String sender = settings.includeSender() && r.sender() != null ? " de " + r.sender() : "";
        return switch (r.kind()) {
            case PAGO_CONFIRMADO -> "Pago recibido por " + amount + " desde " + entity + sender + ".";
            case POSIBLE_PAGO -> amount != null
                    ? "Nueva notificación de " + entity + " por " + amount + sender + ". Verifica el pago."
                    : "Nueva notificación de " + entity + sender + ". Verifica el pago.";
            case ALERTA_CONECTOR -> "Atención: la conexión de " + (r.sourceLabel() != null ? r.sourceLabel() : "un conector")
                    + " está desconectada.";
            case NONE -> "";
        };
    }
}
