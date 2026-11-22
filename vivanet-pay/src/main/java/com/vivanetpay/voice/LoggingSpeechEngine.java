package com.vivanetpay.voice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Motor de voz que solo escribe en log y memoriza las frases. Para pruebas, Linux y modo sin UI.
 * Registra la concurrencia máxima observada para verificar que no hay solapamientos.
 */
public final class LoggingSpeechEngine implements SpeechEngine {
    private static final Logger log = LoggerFactory.getLogger(LoggingSpeechEngine.class);

    private final List<String> spoken = new ArrayList<>();
    private final long delayMillis;
    private int active;
    private int maxConcurrent;
    private volatile boolean available = true;
    private volatile boolean failNext;

    public LoggingSpeechEngine() {
        this(0);
    }

    /** @param delayMillis duración simulada de cada reproducción */
    public LoggingSpeechEngine(long delayMillis) {
        this.delayMillis = delayMillis;
    }

    @Override
    public String id() {
        return "logging";
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    /** Hace fallar la próxima reproducción (pruebas de Flujo E paso 37). */
    public void failNext() {
        this.failNext = true;
    }

    @Override
    public void speak(String text, VoiceSettings settings) throws SpeechException {
        synchronized (this) {
            active++;
            maxConcurrent = Math.max(maxConcurrent, active);
        }
        try {
            if (failNext) {
                failNext = false;
                throw new SpeechException("Fallo simulado de audio");
            }
            if (delayMillis > 0) {
                Thread.sleep(delayMillis);
            }
            log.info("[VOZ] {}", text);
            synchronized (this) {
                spoken.add(text);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SpeechException("Interrumpido", e);
        } finally {
            synchronized (this) {
                active--;
            }
        }
    }

    public synchronized List<String> spoken() {
        return List.copyOf(spoken);
    }

    public synchronized int maxConcurrent() {
        return maxConcurrent;
    }
}
