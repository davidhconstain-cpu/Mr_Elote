package com.vivanetpay.integration.android;

import com.vivanetpay.domain.ConnectorType;
import com.vivanetpay.integration.Connector;
import com.vivanetpay.integration.ConnectorDependencies;
import com.vivanetpay.integration.ConnectorProvider;

/** Proveedor SPI del conector Android ({@code connectors.enabled=android}); id por defecto {@code android-principal}. */
public final class AndroidWebhookConnectorProvider implements ConnectorProvider {

    @Override
    public ConnectorType type() {
        return ConnectorType.ANDROID_WEBHOOK;
    }

    @Override
    public Connector create(ConnectorDependencies dependencies) {
        return new AndroidWebhookConnector(dependencies);
    }
}
