package com.vivanetpay.persistence;

import com.vivanetpay.support.TestApp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RNF-013, IDD §8.5: respaldo con API de backup, integrity_check y restauración probada. */
class BackupRestoreTest {
    @TempDir
    Path dir;

    @Test
    void respaldaYRestaura() throws Exception {
        TestApp app = TestApp.create(dir.resolve("data"));
        try {
            app.ctx.ingestionService().ingest(app.nequi("b-1", "Ana te envió $10.000"));
            app.ctx.ingestionService().ingest(app.nequi("b-2", "Ana te envió $20.000"));
            assertThat(app.ctx.maintenance().integrityCheck()).isEmpty();

            Path backup = app.ctx.maintenance().backupToDirectory(dir.resolve("backups"));
            assertThat(Files.size(backup)).isPositive();
            assertThat(DatabaseMaintenance.integrityCheck(backup)).isEmpty();

            app.ctx.ingestionService().ingest(app.nequi("b-3", "Ana te envió $30.000"));
            assertThat(app.ctx.transactionRepository().count()).isEqualTo(3);

            app.ctx.maintenance().restoreFrom(backup);
            assertThat(app.ctx.transactionRepository().count()).isEqualTo(2);
            assertThat(app.ctx.maintenance().integrityCheck()).isEmpty();
            // tras restaurar, el mismo evento se puede volver a ingerir sin duplicar
            app.ctx.ingestionService().ingest(app.nequi("b-3", "Ana te envió $30.000"));
            app.ctx.ingestionService().ingest(app.nequi("b-3", "Ana te envió $30.000"));
            assertThat(app.ctx.transactionRepository().count()).isEqualTo(3);
        } finally {
            app.ctx.close();
        }
    }

    @Test
    void noRestauraUnArchivoCorrupto() throws Exception {
        TestApp app = TestApp.create(dir.resolve("data2"));
        try {
            Path bad = dir.resolve("corrupto.db");
            Files.writeString(bad, "esto no es una base sqlite".repeat(100));
            assertThat(DatabaseMaintenance.integrityCheck(bad)).isNotEmpty();
            assertThatThrownBy(() -> app.ctx.maintenance().restoreFrom(bad)).isInstanceOf(PersistenceException.class);
            assertThat(app.ctx.maintenance().integrityCheck()).isEmpty();
        } finally {
            app.ctx.close();
        }
    }
}
