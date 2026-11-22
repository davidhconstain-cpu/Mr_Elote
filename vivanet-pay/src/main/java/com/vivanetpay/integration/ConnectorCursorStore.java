package com.vivanetpay.integration;

import java.util.Optional;

/**
 * Cursor de sincronización persistente por conector (Flujo B paso 17, Flujo F paso 41).
 * Implementado por {@code persistence.ConnectorRepository}.
 * <p>
 * REGLA: llamar a {@link #commitCursor} solo después de que {@code EventSink.ingest} haya devuelto
 * para todos los eventos anteriores al cursor. Así, tras un reinicio, se re-sincroniza desde el último cursor
 * confirmado y la idempotencia evita duplicados.
 */
public interface ConnectorCursorStore {

    Optional<String> loadCursor(String connectorId);

    void commitCursor(String connectorId, String cursor);
}
