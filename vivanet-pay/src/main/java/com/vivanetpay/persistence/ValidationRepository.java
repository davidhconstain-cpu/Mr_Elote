package com.vivanetpay.persistence;

import com.vivanetpay.domain.Validation;
import com.vivanetpay.domain.ValidationResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Repositorio de {@code validation}. */
public final class ValidationRepository {
    private final Database db;

    public ValidationRepository(Database db) {
        this.db = db;
    }

    public long insert(Connection c, Validation v) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO validation (event_id, rule, result, reason, "
                + "validated_at, actor) VALUES (?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, v.eventId());
            ps.setString(2, v.rule());
            ps.setString(3, v.result().name());
            ps.setString(4, v.reason());
            Jdbc.setInstant(ps, 5, v.validatedAt());
            ps.setString(6, v.actor());
            ps.executeUpdate();
            return Jdbc.generatedId(ps);
        }
    }

    public List<Validation> findByEvent(long eventId) {
        return db.withConnection(c -> {
            List<Validation> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM validation WHERE event_id = ? ORDER BY id")) {
                ps.setLong(1, eventId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new Validation(rs.getLong("id"), rs.getLong("event_id"), rs.getString("rule"),
                                ValidationResult.valueOf(rs.getString("result")), rs.getString("reason"),
                                Jdbc.getInstant(rs, "validated_at"), rs.getString("actor")));
                    }
                }
            }
            return out;
        });
    }
}
