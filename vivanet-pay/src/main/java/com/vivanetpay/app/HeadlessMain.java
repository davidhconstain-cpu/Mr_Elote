package com.vivanetpay.app;

import com.vivanetpay.domain.CopAmount;
import com.vivanetpay.reporting.DashboardSummary;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

/**
 * Ejecuta el núcleo sin interfaz (diagnóstico, servidores de prueba, Linux).
 * <pre>
 * mvn -q compile exec:java                      # queda en ejecución hasta Ctrl+C
 * mvn -q compile exec:java -Dexec.args=--once   # arranca, imprime resumen y sale
 * </pre>
 * Antes de cualquier log fija {@value AppConfig#LOG_DIR_PROPERTY} al directorio de datos.
 */
public final class HeadlessMain {
    private HeadlessMain() {
    }

    public static void main(String[] args) throws InterruptedException {
        AppConfig config = AppConfig.load();
        if (System.getProperty(AppConfig.LOG_DIR_PROPERTY) == null) {
            System.setProperty(AppConfig.LOG_DIR_PROPERTY, config.logsDir().toString());
        }
        Bootstrap.Result result = Bootstrap.start(config);
        AppContext ctx = result.context();
        DashboardSummary s = ctx.dashboardService().today();
        System.out.println("Vivanet Pay (sin UI) - datos: " + config.dataDir());
        System.out.println("Base íntegra: " + result.databaseHealthy() + " | Licencia: " + result.license().message());
        System.out.println("Hoy " + s.day() + ": confirmados " + CopAmount.format(s.confirmedTotal()) + " ("
                + s.confirmedCount() + "), por verificar " + CopAmount.format(s.unconfirmedTotal()) + " ("
                + s.unconfirmedCount() + "), pendientes de revisión " + s.pendingReviewCount());
        System.out.println("Conectores: " + result.startedConnectors() + " sin implementación: " + result.missingProviders());
        if (Arrays.asList(args).contains("--once")) {
            ctx.close();
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ctx.close();
            latch.countDown();
        }, "vivanet-shutdown"));
        latch.await();
    }
}
