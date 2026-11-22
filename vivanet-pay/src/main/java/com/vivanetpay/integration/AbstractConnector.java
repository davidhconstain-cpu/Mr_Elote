package com.vivanetpay.integration;

import com.vivanetpay.domain.ConnectorState;
import com.vivanetpay.domain.ConnectorStatus;
import com.vivanetpay.domain.ConnectorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Base para conectores: gestiona estado, listeners y pausa. Las subclases implementan
 * {@link #doStart()}, {@link #doStop()} y opcionalmente {@link #onPause()}/{@link #onResume()}, y usan
 * {@link #updateStatus}/{@link #markSuccess()} para informar su estado real.
 */
public abstract class AbstractConnector implements Connector {
    private static final Logger log = LoggerFactory.getLogger(AbstractConnector.class);

    private final String id;
    private final ConnectorType type;
    protected final Clock clock;
    private final List<ConnectorStateListener> listeners = new CopyOnWriteArrayList<>();
    private volatile ConnectorState state;
    private volatile boolean paused;

    protected AbstractConnector(String id, ConnectorType type, Clock clock) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.state = new ConnectorState(id, type, ConnectorStatus.DESCONECTADO, "No iniciado", null, clock.instant());
    }

    @Override
    public final String id() {
        return id;
    }

    @Override
    public final ConnectorType type() {
        return type;
    }

    @Override
    public ConnectorState state() {
        return state;
    }

    public boolean isPaused() {
        return paused;
    }

    @Override
    public final synchronized void start() {
        doStart();
    }

    @Override
    public final synchronized void stop() {
        try {
            doStop();
        } finally {
            updateStatus(ConnectorStatus.DESCONECTADO, "Detenido");
        }
    }

    @Override
    public final synchronized void pause() {
        if (paused) {
            return;
        }
        paused = true;
        onPause();
        updateStatus(ConnectorStatus.PAUSADO, "Captura en pausa por el usuario");
    }

    @Override
    public final synchronized void resume() {
        if (!paused) {
            return;
        }
        paused = false;
        updateStatus(ConnectorStatus.DESCONECTADO, "Reanudando...");
        onResume();
    }

    protected abstract void doStart();

    protected abstract void doStop();

    protected void onPause() {
    }

    protected void onResume() {
    }

    /** Cambia el estado y notifica. {@code detail} debe estar en español claro y sin secretos. */
    protected void updateStatus(ConnectorStatus status, String detail) {
        ConnectorState previous;
        ConnectorState next;
        synchronized (this) {
            previous = state;
            next = previous.withStatus(status, detail, clock.instant());
            state = next;
        }
        fire(previous, next);
    }

    /** Registra una sincronización/recepción exitosa (estado CONECTADO, lastSuccessAt = ahora). */
    protected void markSuccess() {
        ConnectorState previous;
        ConnectorState next;
        synchronized (this) {
            previous = state;
            next = new ConnectorState(id, type, ConnectorStatus.CONECTADO, "Conectado", clock.instant(), clock.instant());
            state = next;
        }
        fire(previous, next);
    }

    private void fire(ConnectorState previous, ConnectorState next) {
        for (ConnectorStateListener l : listeners) {
            try {
                l.onStateChanged(previous, next);
            } catch (RuntimeException e) {
                log.error("Listener de conector {} falló", id, e);
            }
        }
    }

    @Override
    public void addStateListener(ConnectorStateListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    @Override
    public void removeStateListener(ConnectorStateListener listener) {
        listeners.remove(listener);
    }
}
