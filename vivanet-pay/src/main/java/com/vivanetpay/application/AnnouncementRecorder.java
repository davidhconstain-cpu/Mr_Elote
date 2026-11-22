package com.vivanetpay.application;

import com.vivanetpay.domain.Actors;
import com.vivanetpay.domain.AnnouncementKind;
import com.vivanetpay.domain.AnnouncementStatus;
import com.vivanetpay.domain.AuditEntry;
import com.vivanetpay.domain.EventStatus;
import com.vivanetpay.domain.VoiceAnnouncement;
import com.vivanetpay.persistence.AuditRepository;
import com.vivanetpay.persistence.Database;
import com.vivanetpay.persistence.PaymentEventRepository;
import com.vivanetpay.persistence.VoiceAnnouncementRepository;
import com.vivanetpay.voice.AnnouncementOutcomeListener;
import com.vivanetpay.voice.AnnouncementRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * Persiste el resultado de cada anuncio (Flujo E pasos 36-37): fila en {@code voice_announcement},
 * {@code announced_at} del evento y, si era "pago confirmado" de un evento VALIDADO_POR_FUENTE, transición a
 * ANUNCIADO con auditoría. Si no se reprodujo, publica {@link AppEvent.AnnouncementNotPlayed} para aviso visual.
 */
public final class AnnouncementRecorder implements AnnouncementOutcomeListener {
    private static final Logger log = LoggerFactory.getLogger(AnnouncementRecorder.class);

    private final Database db;
    private final VoiceAnnouncementRepository announcements;
    private final PaymentEventRepository events;
    private final AuditRepository audit;
    private final AppEventBus bus;

    public AnnouncementRecorder(Database db, VoiceAnnouncementRepository announcements, PaymentEventRepository events,
                                AuditRepository audit, AppEventBus bus) {
        this.db = db;
        this.announcements = announcements;
        this.events = events;
        this.audit = audit;
        this.bus = bus;
    }

    @Override
    public void onOutcome(AnnouncementRequest r, String message, AnnouncementStatus status, Instant queuedAt,
                          Instant finishedAt, String error) {
        try {
            boolean[] promoted = {false};
            db.inTransaction(c -> {
                announcements.insert(c, new VoiceAnnouncement(null, r.eventId(), r.transactionId(), r.kind(), message,
                        status, queuedAt, status == AnnouncementStatus.REPRODUCIDO ? finishedAt : null, error));
                if (status == AnnouncementStatus.REPRODUCIDO && r.eventId() != null) {
                    events.markAnnounced(c, r.eventId(), finishedAt);
                    if (r.kind() == AnnouncementKind.PAGO_CONFIRMADO) {
                        var current = events.findById(c, r.eventId());
                        if (current.isPresent() && current.get().status() == EventStatus.VALIDADO_POR_FUENTE) {
                            events.updateStatus(c, r.eventId(), EventStatus.ANUNCIADO, "Anuncio de pago confirmado reproducido",
                                    finishedAt);
                            audit.insert(c, new AuditEntry(null, AuditEntry.ENTITY_EVENT, r.eventId().toString(),
                                    AuditEntry.ACTION_STATUS_CHANGE, EventStatus.VALIDADO_POR_FUENTE.name(),
                                    EventStatus.ANUNCIADO.name(), Actors.VOICE, null, r.correlationId(), finishedAt));
                            promoted[0] = true;
                        }
                    }
                }
                return null;
            });
            if (promoted[0]) {
                bus.publish(new AppEvent.EventStatusChanged(r.eventId(), EventStatus.VALIDADO_POR_FUENTE,
                        EventStatus.ANUNCIADO, Actors.VOICE));
            }
        } catch (RuntimeException e) {
            log.error("No se pudo registrar el resultado del anuncio", e);
        }
        if (status != AnnouncementStatus.REPRODUCIDO && r.kind() != AnnouncementKind.ALERTA_CONECTOR && !r.historical()) {
            bus.publish(new AppEvent.AnnouncementNotPlayed(r.eventId(), message, error));
        }
    }
}
