package com.vivanetpay.persistence;

import com.vivanetpay.domain.ConnectorState;
import com.vivanetpay.domain.ConnectorStatus;
import com.vivanetpay.domain.ConnectorType;
import com.vivanetpay.integration.ConnectorCursorStore;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio de {@code connector}: estado, última sincronización y cursor.
 * <p>
 * {@link #commitCursor} debe llamarse SOLO después de persistir los eventos (Flujo B paso 17); es la
 * implementación de {@link ConnectorCursorStore} que reciben los conectores.
 */
public final class ConnectorRepository implements ConnectorCursorStore {
    private final Database db;
    private final Clock clock;

    public ConnectorRepository(Database db, Clock clock) {
        this.db = db;
        this.clock = clock;
    }

    /** Inserta o actualiza el estado (conserva cursor y last_success_at si el nuevo es null). */
    public void saveState(ConnectorState s) {
        db.inTransaction(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO connector (id, type, enabled, connection_status, "
                    + "status_detail, last_success_at, last_error_at, updated_at) VALUES (?,?,1,?,?,?,?,?) "
                    + "ON CONFLICT(id) DO UPDATE SET type = excluded.type, connection_status = excluded.connection_status, "
                    + "status_detail = excluded.status_detail, "
                    + "last_success_at = COALESCE(excluded.last_success_at, connector.last_success_at), "
                    + "last_error_at = COALESCE(excluded.last_error_at, connector.last_error_at), "
                    + "updated_at = excluded.updated_at")) {
                ps.setString(1, s.connectorId());
                ps.setString(2, s.type().name());
                ps.setString(3, s.status().name());
                ps.setString(4, s.detail());
                Jdbc.setInstant(ps, 5, s.lastSuccessAt());
                Jdbc.setInstant(ps, 6, s.status() == ConnectorStatus.ERROR ? s.updatedAt() : null);
                Jdbc.setInstant(ps, 7, s.updatedAt());
                ps.executeUpdate();
            }
            return null;
        });
    }

    public Optional<ConnectorState> find(String connectorId) {
        return db.withConnection(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM connector WHERE id = ?")) {
                ps.setString(1, connectorId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(map(rs)) : Optional.empty();
                }
            }
        });
    }

    public List<ConnectorState> findAll() {
        return db.withConnection(c -> {
            List<ConnectorState> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM connector ORDER BY id");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
            return out;
        });
    }

    private static ConnectorState map(ResultSet rs) throws java.sql.SQLException {
        return new ConnectorState(rs.getString("id"), ConnectorType.valueOf(rs.getString("type")),
                ConnectorStatus.valueOf(rs.getString("connection_status")), rs.getString("status_detail"),
                Jdbc.getInstant(rs, "last_success_at"), Jdbc.getInstant(rs, "updated_at"));
    }

    public void setEnabled(String connectorId, boolean enabled) {
        db.withConnection(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE connector SET enabled = ?, updated_at = ? WHERE id = ?")) {
                ps.setInt(1, enabled ? 1 : 0);
                Jdbc.setInstant(ps, 2, clock.instant());
                ps.setString(3, connectorId);
                return ps.executeUpdate();
            }
        });
    }

    @Override
    public Optional<String> loadCursor(String connectorId) {
        return db.withConnection(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT cursor FROM connector WHERE id = ?")) {
                ps.setString(1, connectorId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.ofNullable(rs.getString(1)) : Optional.empty();
                }
            }
        });
    }

    /**
     * Avanza el cursor. Crea la fila del conector si no existe (estado DESCONECTADO).
     * Llamar solo después de que los eventos previos al cursor estén persistidos.
     */
    @Override
    public void commitCursor(String connectorId, String cursor) {
        db.inTransaction(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE connector SET cursor = ?, cursor_updated_at = ?, "
                    + "updated_at = ? WHERE id = ?")) {
                ps.setString(1, cursor);
                Jdbc.setInstant(ps, 2, clock.instant());
                Jdbc.setInstant(ps, 3, clock.instant());
                ps.setString(4, connectorId);
                if (ps.executeUpdate() == 1) {
                    return null;
                }
            }
            throw new PersistenceException("Conector no registrado: " + connectorId
                    + " (debe guardarse su estado antes de avanzar el cursor)");
        });
    }
}
