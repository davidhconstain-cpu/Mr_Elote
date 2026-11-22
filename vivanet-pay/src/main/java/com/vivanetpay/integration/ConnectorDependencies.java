package com.vivanetpay.integration;

import com.vivanetpay.application.EventSink;
import com.vivanetpay.security.SecretStore;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;

/**
 * Dependencias que el núcleo entrega a cada conector al crearlo.
 *
 * @param connectorId identificador asignado (p. ej. "gmail-principal")
 * @param sink        único punto de entrada al núcleo
 * @param cursorStore cursor persistente
 * @param secretStore almacén de secretos (tokens, secreto del webhook)
 * @param settings    propiedades no secretas del conector (prefijo {@code connector.<tipo>.} eliminado)
 * @param dataDir     directorio de datos de la aplicación (fuera del directorio de instalación)
 * @param clock       reloj inyectable
 */
public record ConnectorDependencies(String connectorId, EventSink sink, ConnectorCursorStore cursorStore,
                                    SecretStore secretStore, Map<String, String> settings, Path dataDir,
                                    Clock clock) {
    public ConnectorDependencies {
        Objects.requireNonNull(connectorId, "connectorId");
        Objects.requireNonNull(sink, "sink");
        Objects.requireNonNull(cursorStore, "cursorStore");
        Objects.requireNonNull(secretStore, "secretStore");
        settings = settings == null ? Map.of() : Map.copyOf(settings);
        Objects.requireNonNull(dataDir, "dataDir");
        Objects.requireNonNull(clock, "clock");
    }
}
