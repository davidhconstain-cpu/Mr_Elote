package com.vivanetpay.voice;

import java.time.Duration;

/**
 * Servicio de anuncios de voz (Flujo E). Seguro para hilos; nunca bloquea al llamador por la reproducción.
 */
public interface VoiceService extends AutoCloseable {

    /**
     * Encola un anuncio. No anuncia: tipo NONE, eventos DUPLICADO, históricos o antiguos, ni con voz silenciada
     * (en esos casos notifica OMITIDO a los listeners cuando aplica).
     *
     * @return true si quedó en cola para reproducirse
     */
    boolean enqueue(AnnouncementRequest request);

    /** Silencia/activa la voz de inmediato (RNF-014). Silenciar descarta lo que esté en cola. */
    void setMuted(boolean muted);

    boolean isMuted();

    VoiceSettings settings();

    void updateSettings(VoiceSettings settings);

    /** Reproduce una frase de prueba (pantalla Voz). */
    void testVoice();

    void addOutcomeListener(AnnouncementOutcomeListener listener);

    /** Espera a que la cola quede vacía (pruebas/cierre ordenado). */
    boolean awaitIdle(Duration timeout) throws InterruptedException;

    @Override
    void close();
}
