package com.vivanetpay.application;

import com.vivanetpay.domain.EventStatus;
import com.vivanetpay.support.TestApp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** RNF-005 / CP-009: reintentos concurrentes nunca crean dos movimientos. */
class ConcurrentIngestionTest {
    @TempDir
    Path dir;

    @Test
    void dosHilosMismoEventoUnSoloMovimiento() throws Exception {
        TestApp app = TestApp.create(dir);
        try {
            List<IngestionResult> results = runConcurrently(
                    () -> app.ctx.ingestionService().ingest(app.nequi("c-1", "Ana te envió $10.000")),
                    () -> app.ctx.ingestionService().ingest(app.nequi("c-1", "Ana te envió $10.000")));
            assertThat(results).extracting(IngestionResult::status)
                    .containsExactlyInAnyOrder(EventStatus.PARSEADO, EventStatus.DUPLICADO);
            assertThat(app.ctx.transactionRepository().count()).isEqualTo(1);
        } finally {
            app.ctx.close();
        }
    }

    @Test
    void dosServiciosSobreLaMismaBaseUnSoloMovimiento() throws Exception {
        // Dos contextos (dos colas de ingesta) sobre el MISMO archivo SQLite: la transacción IMMEDIATE y el UNIQUE
        // garantizan un solo movimiento aunque no compartan hilo.
        TestApp a = TestApp.create(dir, Map.of());
        TestApp b = TestApp.create(dir, Map.of());
        try {
            for (int i = 0; i < 10; i++) {
                String id = "race-" + i;
                String text = "Ana te envió $" + (1000 + i);
                List<IngestionResult> results = runConcurrently(
                        () -> a.ctx.ingestionService().ingest(a.nequi(id, text)),
                        () -> b.ctx.ingestionService().ingest(b.nequi(id, text)));
                assertThat(results).extracting(IngestionResult::status)
                        .containsExactlyInAnyOrder(EventStatus.PARSEADO, EventStatus.DUPLICADO);
            }
            assertThat(a.ctx.transactionRepository().count()).isEqualTo(10);
        } finally {
            a.ctx.close();
            b.ctx.close();
        }
    }

    @SafeVarargs
    private static List<IngestionResult> runConcurrently(Callable<IngestionResult>... tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.length);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<IngestionResult>> futures = new java.util.ArrayList<>();
            for (Callable<IngestionResult> t : tasks) {
                futures.add(pool.submit(() -> {
                    go.await();
                    return t.call();
                }));
            }
            go.countDown();
            List<IngestionResult> out = new java.util.ArrayList<>();
            for (Future<IngestionResult> f : futures) {
                out.add(f.get());
            }
            return out;
        } finally {
            pool.shutdownNow();
        }
    }
}
