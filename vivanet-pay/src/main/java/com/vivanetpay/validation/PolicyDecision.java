package com.vivanetpay.validation;

import com.vivanetpay.domain.AnnouncementKind;
import com.vivanetpay.domain.EventStatus;

import java.util.Objects;

/**
 * Decisión de la política: estado final del evento y tipo de anuncio.
 *
 * @param status       PARSEADO, PENDIENTE_REVISION, VALIDADO_POR_FUENTE o RECHAZADO
 * @param announcement tipo de anuncio; PAGO_CONFIRMADO solo si status es VALIDADO_POR_FUENTE
 * @param reason       motivo legible
 * @param rule         regla de política aplicada (p. ej. {@code AUTO_ANDROID_NEQUI}); nullable. Se registra en la
 *                     tabla validation junto con el actor SISTEMA.
 */
public record PolicyDecision(EventStatus status, AnnouncementKind announcement, String reason, String rule) {
    public PolicyDecision(EventStatus status, AnnouncementKind announcement, String reason) {
        this(status, announcement, reason, null);
    }

    public PolicyDecision {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(announcement, "announcement");
        if (announcement == AnnouncementKind.PAGO_CONFIRMADO && !status.isConfirmed()) {
            throw new IllegalArgumentException("RN-003: solo un evento confirmado puede anunciarse como pago confirmado");
        }
    }
}
