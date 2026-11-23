package com.vivanetpay.integration.android;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Receptor HTTP con hilos virtuales: concurrencia acotada y liberación de cupos (R-01). */
class VirtualThreadReceiverTest {

    @Test
    void rechazaPorEncimaDelCupoYLiberaAlTerminar() throws Exception {
        try (var vt = Executors.newVirtualThreadPerTaskExecutor()) {
            Executor gate = AndroidWebhookConnector.bounded(vt, 2);
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch started = new CountDownLatch(2);
            AtomicBoolean virtual = new AtomicBoolean(true);
            Runnable slow = () -> {
                virtual.compareAndSet(true, Thread.currentThread().isVirtual());
                started.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            };
            gate.execute(slow);
            gate.execute(slow);
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> gate.execute(() -> { })).isInstanceOf(RejectedExecutionException.class);

            release.countDown();
            CountDownLatch ran = new CountDownLatch(1);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (true) {
                try {
                    gate.execute(ran::countDown);
                    break;
                } catch (RejectedExecutionException e) {
                    assertThat(System.nanoTime()).isLessThan(deadline);
                    Thread.onSpinWait();
                }
            }
            assertThat(ran.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(virtual).isTrue();
        }
    }

    @Test
    void siElDelegadoRechazaDevuelveElCupo() {
        Executor rejecting = r -> { throw new RejectedExecutionException("cerrado"); };
        Executor gate = AndroidWebhookConnector.bounded(rejecting, 1);
        assertThatThrownBy(() -> gate.execute(() -> { })).hasMessage("cerrado");
        assertThatThrownBy(() -> gate.execute(() -> { })).hasMessage("cerrado"); // no "saturado": el cupo volvió
    }
}
