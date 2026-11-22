package com.vivanetpay.integration;

import com.vivanetpay.domain.ConnectorType;

/**
 * Fábrica de conectores descubierta por {@link java.util.ServiceLoader}. Registrar la implementación en
 * {@code src/main/resources/META-INF/services/com.vivanetpay.integration.ConnectorProvider}.
 */
public interface ConnectorProvider {

    ConnectorType type();

    /** Identificador por defecto del conector creado (p. ej. "gmail-principal"). */
    default String defaultConnectorId() {
        return type().configKey() + "-principal";
    }

    /** Crea el conector (sin arrancarlo). */
    Connector create(ConnectorDependencies dependencies);
}
