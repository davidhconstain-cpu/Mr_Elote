package com.vivanetpay.persistence;

import com.vivanetpay.domain.EventSource;
import com.vivanetpay.domain.EventStatus;
import com.vivanetpay.domain.PaymentEvent;
import com.vivanetpay.domain.Transaction;
import com.vivanetpay.domain.Validation;
import com.vivanetpay.domain.ValidationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** IDD §7.2: evento + movimiento + validación + auditoría se guardan todo o nada. */
class AtomicityTest {
    @TempDir
    Path dir;

    private IngestionWriter.Record record(String key) {
        Instant now = Instant.parse("2026-11-22T15:00:00Z");
        PaymentEvent e = new PaymentEvent(null, EventSource.MANUAL, "m", null, now, now, null, null, "texto",
                EventStatus.PARSEADO, "ok", "fp-" + key, key, null, null, "p", 1.0, "cid", false, null, now, now);
        Transaction t = new Transaction(null, null, "Nequi", 1000, "COP", null, null, now, now);
        Validation v = new Validation(null, null, "R", ValidationResult.APROBADO, "ok", now, "SISTEMA");
        return new IngestionWriter.Record(e, null, null, t, List.of(v), List.of(EventStatus.RECIBIDO, EventStatus.PARSEADO));
    }

    @Test
    void falloDentroDeLaTransaccionNoDejaNadaAMedias() {
        Database db = Database.openAndMigrate(dir.resolve("x.db"));
        IngestionWriter writer = new IngestionWriter(new PaymentEventRepository(db), new TransactionRepository(db),
                new ValidationRepository(db), new AuditRepository(db));

        assertThatThrownBy(() -> db.inTransaction(c -> {
            writer.write(c, record("k1"));
            throw new IllegalStateException("fallo simulado después de escribir");
        })).hasMessageContaining("fallo simulado");
        assertThat(counts(db)).containsExactly(0L, 0L, 0L, 0L);

        // Violación de UNIQUE en el segundo registro: revierte también el primero
        assertThatThrownBy(() -> db.inTransaction(c -> {
            writer.write(c, record("k2"));
            writer.write(c, record("k2"));
            return null;
        })).isInstanceOf(PersistenceException.class).satisfies(e -> assertThat(((PersistenceException) e).isUniqueViolation()).isTrue());
        assertThat(counts(db)).containsExactly(0L, 0L, 0L, 0L);

        db.inTransaction(c -> writer.write(c, record("k3")));
        assertThat(counts(db)).containsExactly(1L, 1L, 1L, 3L);
    }

    private static List<Long> counts(Database db) {
        return db.withConnection(c -> {
            List<Long> out = new java.util.ArrayList<>();
            for (String t : new String[]{"payment_event", "payment_transaction", "validation", "audit_log"}) {
                try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + t)) {
                    rs.next();
                    out.add(rs.getLong(1));
                }
            }
            return out;
        });
    }

    @Test
    void recorridoDeEstadosInvalidoSeRechaza() {
        Instant now = Instant.now();
        PaymentEvent e = new PaymentEvent(null, EventSource.MANUAL, "m", null, now, now, null, null, "t",
                EventStatus.ANUNCIADO, null, "f", "k", null, null, null, null, null, false, null, now, now);
        assertThatThrownBy(() -> new IngestionWriter.Record(e, null, null, null, List.of(),
                List.of(EventStatus.RECIBIDO, EventStatus.ANUNCIADO))).isInstanceOf(IllegalStateException.class);
    }
}
