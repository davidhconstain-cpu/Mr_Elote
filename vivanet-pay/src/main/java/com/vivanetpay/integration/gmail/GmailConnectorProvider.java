package com.vivanetpay.integration.gmail;

import com.vivanetpay.domain.ConnectorType;
import com.vivanetpay.integration.Connector;
import com.vivanetpay.integration.ConnectorDependencies;
import com.vivanetpay.integration.ConnectorProvider;

/** Registrado por SPI (META-INF/services). Crea el {@link GmailConnector} con la implementación real de Google. */
public final class GmailConnectorProvider implements ConnectorProvider {

    @Override
    public ConnectorType type() {
        return ConnectorType.GMAIL;
    }

    @Override
    public String defaultConnectorId() {
        return "gmail-principal";
    }

    @Override
    public Connector create(ConnectorDependencies deps) {
        GmailSettings settings = GmailSettings.from(deps.settings(), deps.dataDir());
        GmailAuth auth = new GoogleGmailAuth(settings.clientSecretsPath(), deps.secretStore(), deps.connectorId());
        return new GmailConnector(deps, settings, auth);
    }
}
