package com.vivanetpay.application;

import com.vivanetpay.domain.RawEvent;

import java.util.concurrent.CompletableFuture;

/**
 * Único punto de entrada de los conectores al núcleo (RF-005). Implementado por {@link IngestionService}.
 * <p>
 * Garantías: idempotente (reintentar el mismo evento nunca crea otro movimiento, RNF-005); atómico; el
 * procesamiento ocurre en un hilo de fondo del núcleo. Cuando {@link #ingest} retorna con
 * {@link IngestionResult#isPersisted()}, el evento está en disco y el conector puede avanzar su cursor.
 */
public interface EventSink {

    /**
     * Procesa el evento y espera a que quede persistido. Bloqueante: NUNCA llamar desde el hilo de UI.
     * No lanza excepciones por eventos inválidos (quedan RECHAZADO/PENDIENTE_REVISION/ERROR).
     */
    IngestionResult ingest(RawEvent event);

    /** Versión asíncrona de {@link #ingest}. */
    CompletableFuture<IngestionResult> ingestAsync(RawEvent event);
}
