package com.vivanetpay.application;

import com.vivanetpay.domain.Actors;
import com.vivanetpay.domain.AuditEntry;
import com.vivanetpay.domain.ConnectorState;
import com.vivanetpay.domain.ConnectorStatus;
import com.vivanetpay.integration.ConnectorStateListener;
import com.vivanetpay.persistence.AuditRepository;
import com.vivanetpay.persistence.ConnectorRepository;
import com.vivanetpay.voice.AnnouncementRequest;
import com.vivanetpay.voice.VoiceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persiste y publica cambios de estado de conectores (RF-017, RF-025). Si un conector pasa de CONECTADO a un
 * estado que requiere atención, encola la alerta de voz "Atención: la conexión de [fuente] está desconectada."
 */
public final class ConnectorMonitor implements ConnectorStateListener {
    private static final Logger log = LoggerFactory.getLogger(ConnectorMonitor.class);

    private final ConnectorRepository connectors;
    private final AuditRepository audit;
    private final AppEventBus bus;
    private final VoiceService voice;
    private final boolean voiceAlerts;

    public ConnectorMonitor(ConnectorRepository connectors, AuditRepository audit, AppEventBus bus,
                            VoiceService voice, boolean voiceAlerts) {
        this.connectors = connectors;
        this.audit = audit;
        this.bus = bus;
        this.voice = voice;
        this.voiceAlerts = voiceAlerts;
    }

    @Override
    public void onStateChanged(ConnectorState previous, ConnectorState current) {
        try {
            connectors.saveState(current);
            if (previous == null || previous.status() != current.status()) {
                audit.insert(new AuditEntry(null, AuditEntry.ENTITY_CONNECTOR, current.connectorId(),
                        AuditEntry.ACTION_STATUS_CHANGE, previous == null ? null : previous.status().name(),
                        current.status().name(), Actors.SYSTEM, current.detail(), null, current.updatedAt()));
            }
        } catch (RuntimeException e) {
            log.error("No se pudo persistir el estado del conector {}", current.connectorId(), e);
        }
        bus.publish(new AppEvent.ConnectorStatusChanged(previous, current));
        if (voiceAlerts && voice != null && previous != null && previous.status() == ConnectorStatus.CONECTADO
                && current.status().needsAttention()) {
            voice.enqueue(AnnouncementRequest.connectorAlert(label(current)));
        }
    }

    private static String label(ConnectorState s) {
        return switch (s.type()) {
            case GMAIL -> "Gmail";
            case ANDROID_WEBHOOK -> "Android";
        };
    }
}
