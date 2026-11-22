package com.vivanetpay.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Instantánea inmutable del estado de un conector.
 *
 * @param connectorId   identificador estable del conector (p. ej. "gmail-principal")
 * @param type          tipo de conector
 * @param status        estado de conexión
 * @param detail        mensaje en español claro con acción recomendada (RNF-009); sin secretos
 * @param lastSuccessAt última sincronización/recepción exitosa (nullable)
 * @param updatedAt     instante del cambio de estado
 */
public record ConnectorState(String connectorId, ConnectorType type, ConnectorStatus status,
                             String detail, Instant lastSuccessAt, Instant updatedAt) {
    public ConnectorState {
        Objects.requireNonNull(connectorId, "connectorId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(updatedAt, "updatedAt");
        detail = detail == null ? "" : detail;
    }

    public ConnectorState withStatus(ConnectorStatus newStatus, String newDetail, Instant at) {
        return new ConnectorState(connectorId, type, newStatus, newDetail, lastSuccessAt, at);
    }

    public ConnectorState withSuccess(Instant at) {
        return new ConnectorState(connectorId, type, ConnectorStatus.CONECTADO, detail, at, at);
    }
}
