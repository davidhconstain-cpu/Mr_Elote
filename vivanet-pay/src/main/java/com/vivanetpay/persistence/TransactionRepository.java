package com.vivanetpay.persistence;

import com.vivanetpay.domain.Transaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

/** Repositorio de {@code payment_transaction} (inmutable: solo inserción). */
public final class TransactionRepository {
    private final Database db;

    public TransactionRepository(Database db) {
        this.db = db;
    }

    public long insert(Connection c, Transaction t) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO payment_transaction (event_id, bank, amount, currency, "
                + "sender, reference, occurred_at, created_at) VALUES (?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, t.eventId());
            ps.setString(2, t.bank());
            ps.setLong(3, t.amount());
            ps.setString(4, t.currency());
            ps.setString(5, t.sender());
            ps.setString(6, t.reference());
            Jdbc.setInstant(ps, 7, t.occurredAt());
            Jdbc.setInstant(ps, 8, t.createdAt());
            ps.executeUpdate();
            return Jdbc.generatedId(ps);
        }
    }

    public Optional<Transaction> findByEventId(Connection c, long eventId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM payment_transaction WHERE event_id = ?")) {
            ps.setLong(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Transaction(rs.getLong("id"), rs.getLong("event_id"), rs.getString("bank"),
                        rs.getLong("amount"), rs.getString("currency"), rs.getString("sender"), rs.getString("reference"),
                        Jdbc.getInstant(rs, "occurred_at"), Jdbc.getInstant(rs, "created_at")));
            }
        }
    }

    public Optional<Transaction> findByEventId(long eventId) {
        return db.withConnection(c -> findByEventId(c, eventId));
    }

    public long count() {
        return db.withConnection(c -> {
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM payment_transaction")) {
                rs.next();
                return rs.getLong(1);
            }
        });
    }
}
