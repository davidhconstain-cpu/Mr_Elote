package com.vivanetpay.app;

import com.vivanetpay.domain.ConnectorStatus;
import com.vivanetpay.domain.ConnectorType;
import com.vivanetpay.domain.EventSource;
import com.vivanetpay.domain.RawEvent;
import com.vivanetpay.integration.AbstractConnector;
import com.vivanetpay.integration.Connector;
import com.vivanetpay.integration.ConnectorDependencies;
import com.vivanetpay.integration.ConnectorProvider;

/**
 * Conector de PRUEBA registrado por SPI en src/test/resources. Sirve de ejemplo para los agentes de conectores:
 * ingesta un evento y SOLO después avanza el cursor.
 */
public final class FakeConnectorProvider implements ConnectorProvider {

    @Override
    public ConnectorType type() {
        return ConnectorType.ANDROID_WEBHOOK;
    }

    @Override
    public Connector create(ConnectorDependencies deps) {
        return new FakeConnector(deps);
    }

    /** Conector de ejemplo. */
    public static final class FakeConnector extends AbstractConnector {
        private final ConnectorDependencies deps;

        FakeConnector(ConnectorDependencies deps) {
            super(deps.connectorId(), ConnectorType.ANDROID_WEBHOOK, deps.clock());
            this.deps = deps;
        }

        @Override
        protected void doStart() {
            markSuccess();
        }

        @Override
        protected void doStop() {
        }

        /** Simula la recepción de una notificación. */
        public void receive(String id, String text) {
            if (isPaused()) {
                return;
            }
            var result = deps.sink().ingest(RawEvent.builder(EventSource.ANDROID_NEQUI, id())
                    .externalEventId(id).receivedAt(clock.instant()).sourceTimestamp(clock.instant()).rawText(text).build());
            if (result.isPersisted()) {
                deps.cursorStore().commitCursor(id(), id);
            }
        }

        public void fail(String detail) {
            updateStatus(ConnectorStatus.DESCONECTADO, detail);
        }
    }
}
