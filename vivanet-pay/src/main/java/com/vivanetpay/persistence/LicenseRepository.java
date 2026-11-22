package com.vivanetpay.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

/** Repositorio de {@code license_state}. */
public final class LicenseRepository {

    /** Fila de licencia. */
    public record Row(String licenseId, String status, Instant trialStartedAt, Instant trialEndsAt,
                      Instant lastCheckAt, Instant maxSeenAt, Instant updatedAt) {
    }

    public Optional<Row> find(Connection c, String licenseId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM license_state WHERE license_id = ?")) {
            ps.setString(1, licenseId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Row(rs.getString("license_id"), rs.getString("status"),
                        Jdbc.getInstant(rs, "trial_started_at"), Jdbc.getInstant(rs, "trial_ends_at"),
                        Jdbc.getInstant(rs, "last_check_at"), Jdbc.getInstant(rs, "max_seen_at"),
                        Jdbc.getInstant(rs, "updated_at")));
            }
        }
    }

    public void upsert(Connection c, Row r) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO license_state (license_id, status, trial_started_at, "
                + "trial_ends_at, last_check_at, max_seen_at, updated_at) VALUES (?,?,?,?,?,?,?) "
                + "ON CONFLICT(license_id) DO UPDATE SET status = excluded.status, last_check_at = excluded.last_check_at, "
                + "max_seen_at = excluded.max_seen_at, updated_at = excluded.updated_at")) {
            ps.setString(1, r.licenseId());
            ps.setString(2, r.status());
            Jdbc.setInstant(ps, 3, r.trialStartedAt());
            Jdbc.setInstant(ps, 4, r.trialEndsAt());
            Jdbc.setInstant(ps, 5, r.lastCheckAt());
            Jdbc.setInstant(ps, 6, r.maxSeenAt());
            Jdbc.setInstant(ps, 7, r.updatedAt());
            ps.executeUpdate();
        }
    }
}
