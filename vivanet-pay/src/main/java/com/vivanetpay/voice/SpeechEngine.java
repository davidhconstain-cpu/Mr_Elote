package com.vivanetpay.voice;

/**
 * Motor de síntesis de voz. Las llamadas a {@link #speak} son BLOQUEANTES hasta terminar la reproducción;
 * la {@link AnnouncementQueue} las invoca desde un único hilo, nunca desde el hilo de UI.
 */
public interface SpeechEngine {

    /** Identificador del motor (para diagnóstico). */
    String id();

    /** @return true si hay motor/dispositivo de audio disponible. */
    boolean isAvailable();

    /**
     * Reproduce el texto en español.
     *
     * @throws SpeechException si el audio falla (la cola registra FALLIDO y la UI muestra aviso visual)
     */
    void speak(String text, VoiceSettings settings) throws SpeechException;

    /** Excepción de reproducción. */
    final class SpeechException extends Exception {
        public SpeechException(String message) {
            super(message);
        }

        public SpeechException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
