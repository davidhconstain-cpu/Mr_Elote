package com.vivanetpay.persistence;

import com.vivanetpay.domain.AuditEntry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Repositorio de {@code audit_log} (solo inserción, RN-008, RF-024). */
public final class AuditRepository {
    private final Database db;

    public AuditRepository(Database db) {
        this.db = db;
    }

    public long insert(Connection c, AuditEntry a) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO audit_log (entity_type, entity_id, action, before_value, "
                + "after_value, actor, reason, correlation_id, created_at) VALUES (?,?,?,?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, a.entityType());
            ps.setString(2, a.entityId());
            ps.setString(3, a.action());
            ps.setString(4, a.beforeValue());
            ps.setString(5, a.afterValue());
            ps.setString(6, a.actor());
            ps.setString(7, a.reason());
            ps.setString(8, a.correlationId());
            Jdbc.setInstant(ps, 9, a.createdAt());
            ps.executeUpdate();
            return Jdbc.generatedId(ps);
        }
    }

    /** Inserta en su propia transacción. */
    public long insert(AuditEntry a) {
        return db.inTransaction(c -> insert(c, a));
    }

    public List<AuditEntry> findByEntity(String entityType, String entityId) {
        return db.withConnection(c -> {
            List<AuditEntry> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT * FROM audit_log WHERE entity_type = ? AND entity_id = ? ORDER BY id")) {
                ps.setString(1, entityType);
                ps.setString(2, entityId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new AuditEntry(rs.getLong("id"), rs.getString("entity_type"), rs.getString("entity_id"),
                                rs.getString("action"), rs.getString("before_value"), rs.getString("after_value"),
                                rs.getString("actor"), rs.getString("reason"), rs.getString("correlation_id"),
                                Jdbc.getInstant(rs, "created_at")));
                    }
                }
            }
            return out;
        });
    }
}
