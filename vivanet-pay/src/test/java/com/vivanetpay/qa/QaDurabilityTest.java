package com.vivanetpay.qa;

import com.vivanetpay.domain.EventStatus;
import com.vivanetpay.support.TestApp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** CP-012: reinicio abrupto. La base debe quedar íntegra, con cursor y movimientos confirmados localmente (RN-010). */
class QaDurabilityTest {
    @TempDir
    Path dir;

    @Test
    void procesoMatadoDeGolpeConservaMovimientosCursorEIntegridad() throws Exception {
        String cp = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        assumeTrue(cp != null && !cp.isBlank(), "classpath no disponible");
        Path javaBin = Path.of(System.getProperty("java.home"), "bin", "java");
        List<String> cmd = new ArrayList<>(List.of(javaBin.toString(), "-Dfile.encoding=UTF-8", "-Duser.timezone=UTC",
                "-cp", cp, AbruptWriterMain.class.getName(), dir.toString()));
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(dir.resolve("child.log").toFile()).start();
        assertThat(p.waitFor(60, TimeUnit.SECONDS)).as("el proceso hijo debe terminar").isTrue();
        assertThat(p.exitValue()).as(new String(java.nio.file.Files.readAllBytes(dir.resolve("child.log"))))
                .isEqualTo(137); // halt(137): murió sin cerrar nada

        // "reinicio": nuevo contexto sobre el mismo directorio (WAL con transacción huérfana)
        TestApp app = TestApp.create(dir);
        try {
            assertThat(app.ctx.maintenance().integrityCheck()).as("integrity_check tras corte").isEmpty();
            assertThat(app.ctx.eventRepository().count()).isEqualTo(3);
            assertThat(app.ctx.transactionRepository().count()).isEqualTo(3);
            assertThat(app.ctx.connectorRepository().loadCursor("gmail-principal")).contains("hist-3");
            assertThat(app.ctx.settingsRepository().get("no.debe.persistir")).as("transacción a medias revertida").isEmpty();
            // los movimientos siguen visibles y el mismo evento reenviado no se duplica
            assertThat(app.ctx.dashboardService().today().unconfirmedCount()).isEqualTo(3);
            assertThat(app.ctx.ingestionService().ingest(app.nequi("abrupt-2", "Ana te envió $20.000")).status())
                    .isEqualTo(EventStatus.DUPLICADO);
            assertThat(app.ctx.transactionRepository().count()).isEqualTo(3);
        } finally {
            app.ctx.close();
        }
    }
}
