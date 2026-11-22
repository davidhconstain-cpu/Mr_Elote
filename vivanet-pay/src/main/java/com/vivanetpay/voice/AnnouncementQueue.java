package com.vivanetpay.voice;

import com.vivanetpay.domain.AnnouncementKind;
import com.vivanetpay.domain.AnnouncementStatus;
import com.vivanetpay.domain.EventStatus;
import com.vivanetpay.observability.Correlation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Cola de anuncios de un único hilo (Flujo E, paso 34): los anuncios se reproducen de uno en uno, sin solaparse.
 * <ul>
 *   <li>No anuncia DUPLICADO (IDD §9.3) ni eventos históricos o con antigüedad mayor a {@code maxAge}
 *       (IDD §9.2: no reproducir anuncios históricos al iniciar).</li>
 *   <li>Con voz silenciada/desactivada registra OMITIDO; el evento sigue visible (CP-011).</li>
 *   <li>Si el motor falla registra FALLIDO; la UI debe mostrar aviso visual (Flujo E paso 37).</li>
 * </ul>
 */
public final class AnnouncementQueue implements VoiceService {
    private static final Logger log = LoggerFactory.getLogger(AnnouncementQueue.class);
    /** Antigüedad máxima por defecto de un pago para anunciarse. */
    public static final Duration DEFAULT_MAX_AGE = Duration.ofMinutes(15);

    private final SpeechEngine engine;
    private final Clock clock;
    private final Duration maxAge;
    private final ExecutorService executor;
    private final List<AnnouncementOutcomeListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicInteger pending = new AtomicInteger();
    private final AtomicLong muteGeneration = new AtomicLong();
    private volatile VoiceSettings settings;
    private volatile boolean muted;

    public AnnouncementQueue(SpeechEngine engine, VoiceSettings settings, Clock clock, Duration maxAge) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.maxAge = Objects.requireNonNull(maxAge, "maxAge");
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "vivanet-voz");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public boolean enqueue(AnnouncementRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.kind() == AnnouncementKind.NONE) {
            return false;
        }
        if (request.eventStatus() == EventStatus.DUPLICADO || request.eventStatus() == EventStatus.RECHAZADO
                || request.eventStatus() == EventStatus.ERROR) {
            log.debug("No se anuncia evento en estado {}", request.eventStatus());
            return false;
        }
        Instant now = clock.instant();
        String message = AnnouncementMessages.build(request, settings);
        if (request.historical()) {
            notifyListeners(request, message, AnnouncementStatus.OMITIDO, now, now, "Evento histórico: no se anuncia");
            return false;
        }
        if (request.occurredAt() != null && request.occurredAt().isBefore(now.minus(maxAge))) {
            notifyListeners(request, message, AnnouncementStatus.OMITIDO, now, now,
                    "Evento con más de " + maxAge.toMinutes() + " minutos de antigüedad: no se anuncia");
            return false;
        }
        if (muted || !settings.enabled()) {
            notifyListeners(request, message, AnnouncementStatus.OMITIDO, now, now, "Voz silenciada");
            return false;
        }
        long generation = muteGeneration.get();
        pending.incrementAndGet();
        try {
            executor.execute(() -> play(request, now, generation));
        } catch (RuntimeException e) {
            pending.decrementAndGet();
            notifyListeners(request, message, AnnouncementStatus.FALLIDO, now, clock.instant(), "Cola de voz cerrada");
            return false;
        }
        return true;
    }

    private void play(AnnouncementRequest request, Instant queuedAt, long generation) {
        try (var c = Correlation.open(request.correlationId())) {
            VoiceSettings current = settings;
            String message = AnnouncementMessages.build(request, current);
            if (muted || !current.enabled() || generation != muteGeneration.get()) {
                notifyListeners(request, message, AnnouncementStatus.OMITIDO, queuedAt, clock.instant(), "Voz silenciada");
                return;
            }
            if (!engine.isAvailable()) {
                notifyListeners(request, message, AnnouncementStatus.FALLIDO, queuedAt, clock.instant(),
                        "Motor de voz no disponible");
                return;
            }
            try {
                engine.speak(message, current);
                notifyListeners(request, message, AnnouncementStatus.REPRODUCIDO, queuedAt, clock.instant(), null);
            } catch (SpeechEngine.SpeechException | RuntimeException e) {
                log.warn("Fallo al reproducir anuncio: {}", e.getMessage());
                notifyListeners(request, message, AnnouncementStatus.FALLIDO, queuedAt, clock.instant(), e.getMessage());
            }
        } finally {
            pending.decrementAndGet();
        }
    }

    private void notifyListeners(AnnouncementRequest r, String message, AnnouncementStatus status,
                                 Instant queuedAt, Instant at, String error) {
        for (AnnouncementOutcomeListener l : listeners) {
            try {
                l.onOutcome(r, message, status, queuedAt, at, error);
            } catch (RuntimeException e) {
                log.error("Listener de anuncios falló", e);
            }
        }
    }

    @Override
    public void setMuted(boolean muted) {
        this.muted = muted;
        if (muted) {
            muteGeneration.incrementAndGet();
        }
    }

    @Override
    public boolean isMuted() {
        return muted;
    }

    @Override
    public VoiceSettings settings() {
        return settings;
    }

    @Override
    public void updateSettings(VoiceSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    @Override
    public void testVoice() {
        executor.execute(() -> {
            try {
                engine.speak("Prueba de voz de Vivanet Pay.", settings);
            } catch (SpeechEngine.SpeechException | RuntimeException e) {
                log.warn("Prueba de voz falló: {}", e.getMessage());
            }
        });
    }

    @Override
    public void addOutcomeListener(AnnouncementOutcomeListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    @Override
    public boolean awaitIdle(Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (pending.get() > 0) {
            if (System.nanoTime() > deadline) {
                return false;
            }
            Thread.sleep(5);
        }
        return true;
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
