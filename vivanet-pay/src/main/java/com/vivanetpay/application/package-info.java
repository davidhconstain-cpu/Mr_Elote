/**
 * Casos de uso y orquestación asíncrona (Flujos B, C, D, E; RNF-008).
 * <ul>
 *   <li>{@link com.vivanetpay.application.EventSink}: único punto de entrada de los conectores
 *       (implementado por {@link com.vivanetpay.application.IngestionService}).</li>
 *   <li>{@link com.vivanetpay.application.ReviewService}: revisión manual con auditoría (Flujo D).</li>
 *   <li>{@link com.vivanetpay.application.DashboardService} / {@link com.vivanetpay.application.HistoryService}: lectura.</li>
 *   <li>{@link com.vivanetpay.application.AppEventBus}: eventos de aplicación para la UI
 *       (los listeners se invocan en hilos de fondo: la UI debe usar {@code Platform.runLater}).</li>
 * </ul>
 * Implementado por: agente núcleo.
 */
package com.vivanetpay.application;
