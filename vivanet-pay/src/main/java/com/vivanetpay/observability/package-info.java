/**
 * Observabilidad: redacción de datos sensibles en logs (RF-018, RNF-012), conversores de Logback
 * y correlación por evento en MDC (IDD §10.3). Implementado por: agente núcleo.
 * <p>
 * Uso: {@code try (var c = Correlation.open(correlationId)) { ... }}; el patrón de log incluye {@code %X{cid}}.
 * El agente de diagnóstico puede añadir aquí el exportador del paquete de diagnóstico redactado.
 */
package com.vivanetpay.observability;
