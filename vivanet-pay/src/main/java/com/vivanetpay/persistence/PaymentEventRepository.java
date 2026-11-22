package com.vivanetpay.persistence;

import com.vivanetpay.domain.EventSource;
import com.vivanetpay.domain.EventStatus;
import com.vivanetpay.domain.PaymentEvent;
import com.vivanetpay.validation.IdempotencyLookup;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Repositorio de {@code payment_event}. Los métodos con {@link Connection} participan en la transacción del llamador. */
public final class PaymentEventRepository {
    private static final String COLUMNS = "id, source, connector_id, external_id, source_timestamp, received_at, "
            + "raw_subject, raw_sender, raw_text, status, status_reason, fingerprint, idempotency_key, "
            + "duplicate_of_event_id, possible_duplicate_of_event_id, parser_id, parse_confidence, correlation_id, "
            + "historical, announced_at, created_at, updated_at";
    /** Condición de "evento original" para idempotencia. */
    private static final String ORIGINAL = "duplicate_of_event_id IS NULL AND status <> 'ERROR'";

    private final Database db;

    public PaymentEventRepository(Database db) {
        this.db = db;
    }

    /** Inserta el evento y devuelve su id. */
    public long insert(Connection c, PaymentEvent e, String headersJson, String extractedJson) throws SQLException {
        String sql = "INSERT INTO payment_event (source, connector_id, external_id, source_timestamp, received_at, "
                + "raw_subject, raw_sender, raw_text, raw_headers_json, schema_version, extracted_json, parser_id, "
                + "parse_confidence, status, status_reason, fingerprint, idempotency_key, duplicate_of_event_id, "
                + "possible_duplicate_of_event_id, correlation_id, historical, announced_at, created_at, updated_at) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int i = 1;
            ps.setString(i++, e.source().name());
            ps.setString(i++, e.connectorId());
            ps.setString(i++, e.externalId());
            Jdbc.setInstant(ps, i++, e.sourceTimestamp());
            Jdbc.setInstant(ps, i++, e.receivedAt());
            ps.setString(i++, e.rawSubject());
            ps.setString(i++, e.rawSender());
            ps.setString(i++, e.rawText() == null ? "" : e.rawText());
            ps.setString(i++, headersJson);
            ps.setInt(i++, 1);
            ps.setString(i++, extractedJson);
            ps.setString(i++, e.parserId());
            Jdbc.setDouble(ps, i++, e.parseConfidence());
            ps.setString(i++, e.status().name());
            ps.setString(i++, e.statusReason());
            ps.setString(i++, e.fingerprint());
            ps.setString(i++, e.idempotencyKey());
            Jdbc.setLong(ps, i++, e.duplicateOfEventId());
            Jdbc.setLong(ps, i++, e.possibleDuplicateOfEventId());
            ps.setString(i++, e.correlationId());
            ps.setInt(i++, e.historical() ? 1 : 0);
            Jdbc.setInstant(ps, i++, e.announcedAt());
            Jdbc.setInstant(ps, i++, e.createdAt());
            Jdbc.setInstant(ps, i, e.updatedAt());
            ps.executeUpdate();
            return Jdbc.generatedId(ps);
        }
    }

    public Optional<PaymentEvent> findById(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT " + COLUMNS + " FROM payment_event WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /**
     * Evento ya confirmado (VALIDADO_POR_FUENTE o ANUNCIADO) vinculado como posible duplicado del dado, en
     * cualquiera de los dos sentidos (R-04, CP-014): el dado apunta a él, o él apunta al dado.
     */
    public Optional<Long> findConfirmedPossibleDuplicatePeer(Connection c, long id) throws SQLException {
        String sql = "SELECT p.id FROM payment_event p, payment_event e WHERE e.id = ? "
                + "AND p.status IN ('VALIDADO_POR_FUENTE','ANUNCIADO') "
                + "AND (p.id = e.possible_duplicate_of_event_id OR p.possible_duplicate_of_event_id = e.id) "
                + "ORDER BY p.id LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getLong(1)) : Optional.empty();
            }
        }
    }

    public Optional<PaymentEvent> findById(long id) {
        return db.withConnection(c -> findById(c, id));
    }

    /** Texto original y JSON de cabeceras/extracción para la bandeja de revisión (solo lectura). */
    public Optional<RawContent> findRawContent(long id) {
        return db.withConnection(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT raw_subject, raw_sender, raw_text, raw_headers_json, extracted_json FROM payment_event WHERE id = ?")) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(new RawContent(rs.getString(1), rs.getString(2), rs.getString(3),
                            rs.getString(4), rs.getString(5)));
                }
            }
        });
    }

    /** Contenido original y extraído de un evento. */
    public record RawContent(String subject, String sender, String text, String headersJson, String extractedJson) {
    }

    /**
     * Cambia el estado validando la máquina de transiciones. @return estado anterior.
     *
     * @throws IllegalStateException si la transición no está permitida o el evento no existe
     */
    public EventStatus updateStatus(Connection c, long id, EventStatus to, String reason, Instant now) throws SQLException {
        PaymentEvent current = findById(c, id).orElseThrow(() -> new IllegalStateException("Evento no existe: " + id));
        current.status().requireTransitionTo(to);
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE payment_event SET status = ?, status_reason = ?, updated_at = ? WHERE id = ? AND status = ?")) {
            ps.setString(1, to.name());
            ps.setString(2, reason);
            Jdbc.setInstant(ps, 3, now);
            ps.setLong(4, id);
            ps.setString(5, current.status().name());
            if (ps.executeUpdate() != 1) {
                throw new IllegalStateException("El estado del evento " + id + " cambió concurrentemente");
            }
        }
        return current.status();
    }

    public void markAnnounced(Connection c, long id, Instant at) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE payment_event SET announced_at = ? WHERE id = ?")) {
            Jdbc.setInstant(ps, 1, at);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    /** Eventos en los estados dados, más recientes primero. */
    public List<PaymentEvent> findByStatuses(Set<EventStatus> statuses, int limit) {
        if (statuses.isEmpty()) {
            return List.of();
        }
        String in = statuses.stream().map(s -> "'" + s.name() + "'").collect(Collectors.joining(","));
        return db.withConnection(c -> {
            List<PaymentEvent> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT " + COLUMNS + " FROM payment_event WHERE status IN ("
                    + in + ") ORDER BY received_at DESC, id DESC LIMIT ?")) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(map(rs));
                    }
                }
            }
            return out;
        });
    }

    public long count() {
        return db.withConnection(c -> {
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM payment_event")) {
                rs.next();
                return rs.getLong(1);
            }
        });
    }

    /** Lookup de idempotencia ligado a la conexión (y transacción) dada. */
    public IdempotencyLookup lookup(Connection c) {
        return new IdempotencyLookup() {
            @Override
            public Optional<Long> findByIdempotencyKey(String key) {
                return single("SELECT id FROM payment_event WHERE idempotency_key = ? AND " + ORIGINAL, key);
            }

            @Override
            public Optional<Long> findByFingerprint(String fingerprint) {
                return single("SELECT id FROM payment_event WHERE fingerprint = ? AND " + ORIGINAL
                        + " ORDER BY id LIMIT 1", fingerprint);
            }

            @Override
            public List<SimilarEvent> findSameAmountBetween(long amount, Instant from, Instant to) {
                String sql = "SELECT e.id, e.source, e.external_id, t.sender, t.occurred_at FROM payment_event e "
                        + "JOIN payment_transaction t ON t.event_id = e.id WHERE t.amount = ? AND t.occurred_at BETWEEN ? AND ? "
                        + "AND e.duplicate_of_event_id IS NULL AND e.status NOT IN ('ERROR','RECHAZADO') ORDER BY e.id";
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setLong(1, amount);
                    Jdbc.setInstant(ps, 2, from);
                    Jdbc.setInstant(ps, 3, to);
                    List<SimilarEvent> out = new ArrayList<>();
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            out.add(new SimilarEvent(rs.getLong(1), EventSource.valueOf(rs.getString(2)), rs.getString(3),
                                    rs.getString(4), Jdbc.getInstant(rs, "occurred_at")));
                        }
                    }
                    return out;
                } catch (SQLException e) {
                    throw new PersistenceException("Error consultando similares", e);
                }
            }

            private Optional<Long> single(String sql, String param) {
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setString(1, param);
                    try (ResultSet rs = ps.executeQuery()) {
                        return rs.next() ? Optional.of(rs.getLong(1)) : Optional.empty();
                    }
                } catch (SQLException e) {
                    throw new PersistenceException("Error consultando idempotencia", e);
                }
            }
        };
    }

    static PaymentEvent map(ResultSet rs) throws SQLException {
        return new PaymentEvent(
                rs.getLong("id"),
                EventSource.valueOf(rs.getString("source")),
                rs.getString("connector_id"),
                rs.getString("external_id"),
                Jdbc.getInstant(rs, "source_timestamp"),
                Jdbc.getInstant(rs, "received_at"),
                rs.getString("raw_subject"),
                rs.getString("raw_sender"),
                rs.getString("raw_text"),
                EventStatus.valueOf(rs.getString("status")),
                rs.getString("status_reason"),
                rs.getString("fingerprint"),
                rs.getString("idempotency_key"),
                Jdbc.getLong(rs, "duplicate_of_event_id"),
                Jdbc.getLong(rs, "possible_duplicate_of_event_id"),
                rs.getString("parser_id"),
                Jdbc.getDouble(rs, "parse_confidence"),
                rs.getString("correlation_id"),
                rs.getInt("historical") == 1,
                Jdbc.getInstant(rs, "announced_at"),
                Jdbc.getInstant(rs, "created_at"),
                Jdbc.getInstant(rs, "updated_at"));
    }
}
