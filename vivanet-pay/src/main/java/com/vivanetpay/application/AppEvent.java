package com.vivanetpay.application;

import com.vivanetpay.domain.ConnectorState;
import com.vivanetpay.domain.EventSource;
import com.vivanetpay.domain.EventStatus;

import java.time.Instant;

/**
 * Eventos de aplicación publicados en {@link AppEventBus}. Inmutables.
 */
public sealed interface AppEvent {

    /** Se persistió un movimiento nuevo (confirmado o por verificar). Refrescar tablero/historial. */
    record TransactionRecorded(long eventId, Long transactionId, EventStatus status, EventSource source, String bank,
                               Long amount, Instant occurredAt) implements AppEvent {
    }

    /** Un evento quedó en la bandeja de revisión (PARSEADO o PENDIENTE_REVISION). */
    record EventNeedsReview(long eventId, EventStatus status, String reason) implements AppEvent {
    }

    /** Cambió el estado de un evento (revisión manual, anuncio, corrección). */
    record EventStatusChanged(long eventId, EventStatus from, EventStatus to, String actor) implements AppEvent {
    }

    /** Cambió el estado de un conector (RF-017, RF-025). */
    record ConnectorStatusChanged(ConnectorState previous, ConnectorState current) implements AppEvent {
    }

    /** El anuncio de voz falló o se omitió por voz silenciada: mostrar aviso visual (Flujo E paso 37, IDD §9.2). */
    record AnnouncementNotPlayed(Long eventId, String message, String reason) implements AppEvent {
    }
}
