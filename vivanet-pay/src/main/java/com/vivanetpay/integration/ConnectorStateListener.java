package com.vivanetpay.integration;

import com.vivanetpay.domain.ConnectorState;

/** Escucha cambios de estado de conectores. Se invoca en el hilo del conector: no bloquear. */
@FunctionalInterface
public interface ConnectorStateListener {
    void onStateChanged(ConnectorState previous, ConnectorState current);
}
