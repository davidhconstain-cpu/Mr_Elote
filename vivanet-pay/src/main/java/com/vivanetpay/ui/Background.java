package com.vivanetpay.ui;

import javafx.application.Platform;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Ejecuta trabajo bloqueante (base de datos, red) fuera del hilo de JavaFX y entrega el resultado en él. */
public final class Background {
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "vivanet-ui-bg");
        t.setDaemon(true);
        return t;
    });

    private Background() {
    }

    public static <T> void run(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        POOL.execute(() -> {
            try {
                T result = work.call();
                Platform.runLater(() -> onSuccess.accept(result));
            } catch (Throwable e) {
                Platform.runLater(() -> onError.accept(e));
            }
        });
    }

    public static void run(Runnable work, Runnable onDone, Consumer<Throwable> onError) {
        run(() -> {
            work.run();
            return Boolean.TRUE;
        }, ok -> onDone.run(), onError);
    }

    public static void execute(Runnable r) {
        POOL.execute(r);
    }
}
