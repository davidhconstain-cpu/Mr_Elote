package com.vivanetpay.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registro de conectores activos. Propaga los cambios de estado a listeners globales
 * (la aplicación los persiste y publica {@code ConnectorStatusChanged}). Seguro para hilos.
 */
public final class ConnectorRegistry {
    private static final Logger log = LoggerFactory.getLogger(ConnectorRegistry.class);

    private final Map<String, Connector> connectors = new ConcurrentHashMap<>();
    private final List<ConnectorStateListener> globalListeners = new CopyOnWriteArrayList<>();
    private final ConnectorStateListener relay = (prev, cur) -> {
        for (ConnectorStateListener l : globalListeners) {
            try {
                l.onStateChanged(prev, cur);
            } catch (RuntimeException e) {
                log.error("Listener global de conectores falló", e);
            }
        }
    };

    public void register(Connector connector) {
        Objects.requireNonNull(connector, "connector");
        if (connectors.putIfAbsent(connector.id(), connector) != null) {
            throw new IllegalArgumentException("Conector ya registrado: " + connector.id());
        }
        connector.addStateListener(relay);
    }

    /** Quita un conector (el llamador debe haberlo detenido). Devuelve el conector retirado, si existía. */
    public Optional<Connector> unregister(String id) {
        Connector c = connectors.remove(id);
        if (c != null) {
            c.removeStateListener(relay);
        }
        return Optional.ofNullable(c);
    }

    public Optional<Connector> get(String id) {
        return Optional.ofNullable(connectors.get(id));
    }

    public Collection<Connector> all() {
        return List.copyOf(connectors.values());
    }

    public void addGlobalListener(ConnectorStateListener listener) {
        globalListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** Arranca todos; un fallo en uno no impide arrancar los demás (RNF-006). */
    public void startAll() {
        for (Connector c : connectors.values()) {
            try {
                c.start();
            } catch (RuntimeException e) {
                log.error("No se pudo iniciar el conector {}", c.id(), e);
            }
        }
    }

    public void pauseAll() {
        connectors.values().forEach(Connector::pause);
    }

    public void resumeAll() {
        connectors.values().forEach(Connector::resume);
    }

    public void stopAll() {
        for (Connector c : connectors.values()) {
            try {
                c.stop();
            } catch (RuntimeException e) {
                log.error("Error al detener el conector {}", c.id(), e);
            }
        }
    }
}
