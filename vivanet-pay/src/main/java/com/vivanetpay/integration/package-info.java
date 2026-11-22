/**
 * Integración: contratos comunes de conectores (RF-005, RF-017, RNF-011).
 * <p>
 * Contratos estables para los agentes de Gmail y Android:
 * <ul>
 *   <li>{@link com.vivanetpay.integration.Connector} (o extender {@link com.vivanetpay.integration.AbstractConnector}).</li>
 *   <li>{@link com.vivanetpay.integration.ConnectorProvider}: SPI registrado en
 *       {@code META-INF/services/com.vivanetpay.integration.ConnectorProvider}; {@code Bootstrap} crea y arranca
 *       los conectores habilitados en {@code connectors.enabled}.</li>
 *   <li>{@link com.vivanetpay.integration.ConnectorDependencies}: lo que recibe cada conector
 *       ({@link com.vivanetpay.application.EventSink}, cursor, secretos, configuración).</li>
 *   <li>{@link com.vivanetpay.integration.ConnectorCursorStore}: avanzar el cursor SOLO después de que
 *       {@code EventSink.ingest} devolvió (evento ya persistido; Flujo B paso 17).</li>
 * </ul>
 * Los conectores nunca se ejecutan en el hilo de UI y nunca simulan conexión (Flujo A paso 7).
 */
package com.vivanetpay.integration;
