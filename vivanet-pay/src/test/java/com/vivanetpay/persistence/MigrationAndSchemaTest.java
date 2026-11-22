package com.vivanetpay.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MigrationAndSchemaTest {
    @TempDir
    Path dir;

    @Test
    void migraUnaVezYEsIdempotente() {
        Database db = Database.openAndMigrate(dir.resolve("a.db"));
        MigrationRunner runner = new MigrationRunner(db);
        assertThat(runner.currentVersion()).isEqualTo(1);
        assertThat(runner.migrate()).isEqualTo(1);
        String journal = db.withConnection(c -> {
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("PRAGMA journal_mode")) {
                rs.next();
                return rs.getString(1);
            }
        });
        assertThat(journal).isEqualToIgnoringCase("wal");
        int fk = db.withConnection(c -> {
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("PRAGMA foreign_keys")) {
                rs.next();
                return rs.getInt(1);
            }
        });
        assertThat(fk).isEqualTo(1);
    }

    @Test
    void creaTodasLasTablasDelIdd() {
        Database db = Database.openAndMigrate(dir.resolve("b.db"));
        for (String t : new String[]{"payment_event", "payment_transaction", "connector", "validation",
                "voice_announcement", "audit_log", "app_setting", "license_state", "schema_version"}) {
            int n = db.withConnection(c -> {
                try (Statement st = c.createStatement();
                     ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='" + t + "'")) {
                    rs.next();
                    return rs.getInt(1);
                }
            });
            assertThat(n).as(t).isEqualTo(1);
        }
    }

    @Test
    void noSePermiteBorradoFisicoNiImportesNoPositivosRN008() {
        Database db = Database.openAndMigrate(dir.resolve("c.db"));
        db.withConnection(c -> {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("INSERT INTO payment_event (source, connector_id, received_at, status, fingerprint, "
                        + "idempotency_key, created_at, updated_at) VALUES ('MANUAL','m',1,'PARSEADO','f','k',1,1)");
                st.executeUpdate("INSERT INTO audit_log (entity_type, entity_id, action, actor, created_at) "
                        + "VALUES ('x','1','A','yo',1)");
            }
            return null;
        });
        assertThatThrownBy(() -> db.withConnection(c -> c.createStatement().executeUpdate("DELETE FROM payment_event")))
                .hasMessageContaining("RN-008");
        assertThatThrownBy(() -> db.withConnection(c -> c.createStatement().executeUpdate("DELETE FROM audit_log")))
                .hasMessageContaining("RN-008");
        assertThatThrownBy(() -> db.withConnection(c -> c.createStatement().executeUpdate("UPDATE audit_log SET actor='z'")))
                .hasMessageContaining("RN-008");
        assertThatThrownBy(() -> db.withConnection(c -> c.createStatement().executeUpdate(
                "UPDATE payment_event SET raw_text='alterado'"))).hasMessageContaining("inmutable");
        assertThatThrownBy(() -> db.withConnection(c -> c.createStatement().executeUpdate(
                "INSERT INTO payment_transaction (event_id, amount, occurred_at, created_at) VALUES (1, 0, 1, 1)")))
                .hasMessageContaining("CHECK");
        assertThatThrownBy(() -> db.withConnection(c -> c.createStatement().executeUpdate(
                "INSERT INTO payment_transaction (event_id, amount, currency, occurred_at, created_at) VALUES (1, 5, 'USD', 1, 1)")))
                .hasMessageContaining("CHECK");
    }

    @Test
    void uniqueDeIdExternoEnBaseDeDatos() {
        Database db = Database.openAndMigrate(dir.resolve("d.db"));
        String ins = "INSERT INTO payment_event (source, connector_id, external_id, received_at, status, fingerprint, "
                + "idempotency_key, created_at, updated_at) VALUES ('GMAIL','g','m1',1,'PARSEADO','%s','%s',1,1)";
        db.withConnection(c -> c.createStatement().executeUpdate(String.format(ins, "f1", "k1")));
        assertThatThrownBy(() -> db.withConnection(c -> c.createStatement().executeUpdate(String.format(ins, "f2", "k2"))))
                .satisfies(e -> assertThat(PersistenceException.isUniqueViolation(e)).isTrue());
    }

    @Test
    void divisorDeSentenciasRespetaTriggers() {
        var parts = MigrationRunner.split("""
                -- comentario;
                CREATE TABLE a (x INT);
                CREATE TRIGGER t BEFORE DELETE ON a
                BEGIN
                    SELECT RAISE(ABORT, 'no');
                END;
                CREATE INDEX i ON a(x);
                """);
        assertThat(parts).hasSize(3);
        assertThat(parts.get(1)).startsWith("CREATE TRIGGER").endsWith("END;");
    }
}
