package com.vivanetpay.ui;

import javafx.application.Platform;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

/**
 * Ejecuta trabajo bloqueante (base de datos, red) fuera del hilo de JavaFX y entrega el resultado en él.
 * <p>
 * Usa hilos virtuales (Java 21+): cada tarea tiene su propio hilo barato y con nombre, sin dimensionar un pool.
 * Un semáforo mantiene el límite de 3 tareas simultáneas que tenía el pool anterior, para no saturar la única
 * conexión SQLite con consultas de interfaz concurrentes.
 */
public final class Background {
    private static final int MAX_CONCURRENT = 3;
    private static final Semaphore SLOTS = new Semaphore(MAX_CONCURRENT);
    private static final ExecutorService POOL = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("vivanet-ui-bg-", 0).factory());

    private Background() {
    }

    public static <T> void run(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        POOL.execute(() -> {
            T result;
            try {
                SLOTS.acquire();
                try {
                    result = work.call();
                } finally {
                    SLOTS.release();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Platform.runLater(() -> onError.accept(e));
                return;
            } catch (Throwable e) {
                Platform.runLater(() -> onError.accept(e));
                return;
            }
            Platform.runLater(() -> onSuccess.accept(result));
        });
    }

    public static void run(Runnable work, Runnable onDone, Consumer<Throwable> onError) {
        run(() -> {
            work.run();
            return Boolean.TRUE;
        }, ok -> onDone.run(), onError);
    }

    public static void execute(Runnable r) {
        POOL.execute(() -> {
            try {
                SLOTS.acquire();
                try {
                    r.run();
                } finally {
                    SLOTS.release();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }
}
