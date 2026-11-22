package com.vivanetpay.persistence;

import com.vivanetpay.domain.AppTime;
import com.vivanetpay.domain.EventSource;
import com.vivanetpay.domain.EventStatus;
import com.vivanetpay.reporting.BankBreakdown;
import com.vivanetpay.reporting.DashboardSummary;
import com.vivanetpay.reporting.HistoryFilter;
import com.vivanetpay.reporting.HistoryRow;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Consultas de solo lectura para tablero (RF-013) e historial (RF-014, RF-019). Los días se calculan en
 * America/Bogota y se convierten a rangos UTC [inicio, fin) (CP-015).
 */
public final class ReportingQueries {
    public static final String UNIDENTIFIED_BANK = "Sin identificar";
    private static final String OCCURRED = "COALESCE(t.occurred_at, e.source_timestamp, e.received_at)";
    private static final String SELECT_ROW = "SELECT e.id AS event_id, t.id AS tx_id, e.source, e.connector_id, t.bank, "
            + "t.amount, t.sender, t.reference, e.status, e.status_reason, " + OCCURRED + " AS occurred, e.received_at, "
            + "e.duplicate_of_event_id, e.possible_duplicate_of_event_id "
            + "FROM payment_event e LEFT JOIN payment_transaction t ON t.event_id = e.id ";

    private final Database db;

    public ReportingQueries(Database db) {
        this.db = db;
    }

    /** Resumen del día local {@code day}. */
    public DashboardSummary dashboard(LocalDate day, Instant generatedAt) {
        Instant from = AppTime.startOfDay(day);
        Instant to = AppTime.endOfDay(day);
        String confirmed = in(EventStatus.confirmedStatuses());
        String unconfirmed = in(EventStatus.unconfirmedStatuses());
        return db.withConnection(c -> {
            Map<String, long[]> byBank = new LinkedHashMap<>();
            String sql = "SELECT COALESCE(t.bank, '" + UNIDENTIFIED_BANK + "') AS bank, "
                    + "SUM(CASE WHEN e.status IN (" + confirmed + ") THEN t.amount ELSE 0 END) AS ctotal, "
                    + "SUM(CASE WHEN e.status IN (" + confirmed + ") THEN 1 ELSE 0 END) AS ccount, "
                    + "SUM(CASE WHEN e.status IN (" + unconfirmed + ") THEN t.amount ELSE 0 END) AS utotal, "
                    + "SUM(CASE WHEN e.status IN (" + unconfirmed + ") THEN 1 ELSE 0 END) AS ucount "
                    + "FROM payment_transaction t JOIN payment_event e ON e.id = t.event_id "
                    + "WHERE t.occurred_at >= ? AND t.occurred_at < ? AND e.status IN (" + confirmed + "," + unconfirmed + ") "
                    + "GROUP BY 1 ORDER BY (ctotal + utotal) DESC, bank";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                Jdbc.setInstant(ps, 1, from);
                Jdbc.setInstant(ps, 2, to);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        byBank.put(rs.getString("bank"), new long[]{rs.getLong("ctotal"), rs.getLong("ccount"),
                                rs.getLong("utotal"), rs.getLong("ucount")});
                    }
                }
            }
            List<BankBreakdown> breakdown = new ArrayList<>();
            long ct = 0, cc = 0, ut = 0, uc = 0;
            for (var en : byBank.entrySet()) {
                long[] v = en.getValue();
                breakdown.add(new BankBreakdown(en.getKey(), v[0], (int) v[1], v[2], (int) v[3]));
                ct += v[0];
                cc += v[1];
                ut += v[2];
                uc += v[3];
            }
            HistoryRow last = null;
            try (PreparedStatement ps = c.prepareStatement(SELECT_ROW + "WHERE t.id IS NOT NULL AND t.occurred_at >= ? "
                    + "AND t.occurred_at < ? AND e.status IN (" + confirmed + "," + unconfirmed + ") "
                    + "ORDER BY t.occurred_at DESC, e.id DESC LIMIT 1")) {
                Jdbc.setInstant(ps, 1, from);
                Jdbc.setInstant(ps, 2, to);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        last = mapRow(rs);
                    }
                }
            }
            int pending;
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM payment_event WHERE status = 'PENDIENTE_REVISION'");
                 ResultSet rs = ps.executeQuery()) {
                rs.next();
                pending = rs.getInt(1);
            }
            return new DashboardSummary(day, ct, (int) cc, ut, (int) uc, pending, breakdown, last, generatedAt);
        });
    }

    /** Historial filtrado, más reciente primero. */
    public List<HistoryRow> history(HistoryFilter f) {
        StringBuilder where = new StringBuilder("WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        if (f.fromDate() != null) {
            where.append("AND ").append(OCCURRED).append(" >= ? ");
            params.add(AppTime.startOfDay(f.fromDate()).toEpochMilli());
        }
        if (f.toDate() != null) {
            where.append("AND ").append(OCCURRED).append(" < ? ");
            params.add(AppTime.endOfDay(f.toDate()).toEpochMilli());
        }
        if (!f.sources().isEmpty()) {
            where.append("AND e.source IN (").append(inSources(f.sources())).append(") ");
        }
        if (!f.statuses().isEmpty()) {
            where.append("AND e.status IN (").append(in(f.statuses())).append(") ");
        }
        if (f.minAmount() != null) {
            where.append("AND t.amount >= ? ");
            params.add(f.minAmount());
        }
        if (f.maxAmount() != null) {
            where.append("AND t.amount <= ? ");
            params.add(f.maxAmount());
        }
        if (f.bank() != null && !f.bank().isBlank()) {
            where.append("AND LOWER(t.bank) = LOWER(?) ");
            params.add(f.bank().trim());
        }
        if (f.text() != null && !f.text().isBlank()) {
            where.append("AND (LOWER(t.sender) LIKE ? ESCAPE '\\' OR LOWER(t.reference) LIKE ? ESCAPE '\\') ");
            String like = "%" + f.text().trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            params.add(like);
            params.add(like);
        }
        String sql = SELECT_ROW + where + "ORDER BY occurred DESC, e.id DESC LIMIT ? OFFSET ?";
        params.add((long) f.limit());
        params.add((long) f.offset());
        return db.withConnection(c -> {
            List<HistoryRow> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                for (int i = 0; i < params.size(); i++) {
                    Object p = params.get(i);
                    if (p instanceof Long l) {
                        ps.setLong(i + 1, l);
                    } else {
                        ps.setString(i + 1, (String) p);
                    }
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(mapRow(rs));
                    }
                }
            }
            return out;
        });
    }

    /** Fila de historial de un evento. */
    public java.util.Optional<HistoryRow> row(long eventId) {
        return db.withConnection(c -> {
            try (PreparedStatement ps = c.prepareStatement(SELECT_ROW + "WHERE e.id = ?")) {
                ps.setLong(1, eventId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? java.util.Optional.of(mapRow(rs)) : java.util.Optional.empty();
                }
            }
        });
    }

    static HistoryRow mapRow(ResultSet rs) throws SQLException {
        return new HistoryRow(rs.getLong("event_id"), Jdbc.getLong(rs, "tx_id"), EventSource.valueOf(rs.getString("source")),
                rs.getString("connector_id"), rs.getString("bank"), Jdbc.getLong(rs, "amount"), rs.getString("sender"),
                rs.getString("reference"), EventStatus.valueOf(rs.getString("status")), rs.getString("status_reason"),
                Jdbc.getInstant(rs, "occurred"), Jdbc.getInstant(rs, "received_at"),
                Jdbc.getLong(rs, "duplicate_of_event_id"), Jdbc.getLong(rs, "possible_duplicate_of_event_id"));
    }

    private static String in(Set<EventStatus> statuses) {
        return statuses.stream().map(s -> "'" + s.name() + "'").collect(Collectors.joining(","));
    }

    private static String inSources(Set<EventSource> sources) {
        return sources.stream().map(s -> "'" + s.name() + "'").collect(Collectors.joining(","));
    }
}
