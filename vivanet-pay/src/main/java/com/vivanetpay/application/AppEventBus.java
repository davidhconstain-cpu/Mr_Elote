package com.vivanetpay.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Bus de eventos de aplicación, síncrono y seguro para hilos. Los listeners se ejecutan en el hilo que publica
 * (normalmente un hilo de fondo): deben ser rápidos y la UI debe pasar a su hilo con {@code Platform.runLater}.
 * Una excepción en un listener se registra y no afecta a los demás.
 */
public final class AppEventBus {
    private static final Logger log = LoggerFactory.getLogger(AppEventBus.class);

    private record Sub(Class<? extends AppEvent> type, Consumer<AppEvent> listener) {
    }

    private final List<Sub> subs = new CopyOnWriteArrayList<>();

    /** Suscripción cancelable. */
    public interface Subscription extends AutoCloseable {
        @Override
        void close();
    }

    @SuppressWarnings("unchecked")
    public <T extends AppEvent> Subscription subscribe(Class<T> type, Consumer<? super T> listener) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(listener, "listener");
        Sub sub = new Sub(type, e -> ((Consumer<T>) listener).accept((T) e));
        subs.add(sub);
        return () -> subs.remove(sub);
    }

    /** Suscribe a todos los eventos. */
    public Subscription subscribeAll(Consumer<AppEvent> listener) {
        return subscribe(AppEvent.class, listener);
    }

    public void publish(AppEvent event) {
        Objects.requireNonNull(event, "event");
        for (Sub s : subs) {
            if (s.type().isInstance(event)) {
                try {
                    s.listener().accept(event);
                } catch (RuntimeException e) {
                    log.error("Listener de {} falló", event.getClass().getSimpleName(), e);
                }
            }
        }
    }
}
