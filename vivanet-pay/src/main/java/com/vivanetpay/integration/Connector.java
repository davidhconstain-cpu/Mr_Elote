package com.vivanetpay.integration;

import com.vivanetpay.domain.ConnectorState;
import com.vivanetpay.domain.ConnectorType;

/**
 * Conector de una fuente externa (Gmail, Android...). Contrato del ciclo de vida:
 * <ul>
 *   <li>{@link #start()} no debe bloquear: lanza su propio hilo/planificador y retorna.</li>
 *   <li>{@link #stop()} libera recursos; debe ser idempotente.</li>
 *   <li>{@link #pause()}/{@link #resume()} (RF-004): en pausa no captura; estado {@code PAUSADO}.</li>
 *   <li>Cada cambio de estado se notifica a los {@link ConnectorStateListener} (RF-017). Si requiere
 *       autorización debe reportar {@code REQUIERE_AUTORIZACION} y NO simular conexión.</li>
 *   <li>Entrega eventos SOLO mediante {@link com.vivanetpay.application.EventSink#ingest}.</li>
 * </ul>
 */
public interface Connector {

    /** Identificador estable (p. ej. "gmail-principal"); clave en la tabla {@code connector}. */
    String id();

    ConnectorType type();

    /** Nombre para la UI y anuncios (p. ej. "Gmail"). */
    default String displayName() {
        return type().name();
    }

    void start();

    void stop();

    void pause();

    void resume();

    /** Estado actual (instantánea inmutable). */
    ConnectorState state();

    void addStateListener(ConnectorStateListener listener);

    void removeStateListener(ConnectorStateListener listener);
}
