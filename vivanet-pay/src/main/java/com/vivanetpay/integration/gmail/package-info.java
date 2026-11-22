/**
 * Conector Gmail (Flujo B, RF-021, CU-02, CP-003/CP-004): OAuth 2.0 con alcance mínimo de solo lectura
 * ({@code gmail.readonly}), búsqueda incremental por remitentes/plantillas configurables, revocación.
 * <p>
 * Lo implementará el AGENTE GMAIL/SEGURIDAD: un {@code GmailConnector extends AbstractConnector} y un
 * {@code GmailConnectorProvider implements ConnectorProvider} registrado por SPI. Debe construir
 * {@link com.vivanetpay.domain.RawEvent} con {@code source=GMAIL}, {@code externalEventId=messageId},
 * llamar a {@link com.vivanetpay.application.EventSink#ingest} y solo después avanzar el cursor
 * ({@link com.vivanetpay.integration.ConnectorCursorStore#commitCursor}). Tokens en
 * {@link com.vivanetpay.security.SecretStore}, nunca en logs ni en el repositorio.
 */
package com.vivanetpay.integration.gmail;
