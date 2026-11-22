package com.vivanetpay.persistence;

import com.vivanetpay.domain.AnnouncementKind;
import com.vivanetpay.domain.AnnouncementStatus;
import com.vivanetpay.domain.VoiceAnnouncement;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Repositorio de {@code voice_announcement}. */
public final class VoiceAnnouncementRepository {
    private final Database db;

    public VoiceAnnouncementRepository(Database db) {
        this.db = db;
    }

    public long insert(Connection c, VoiceAnnouncement v) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO voice_announcement (event_id, transaction_id, kind, "
                + "message, status, queued_at, spoken_at, error) VALUES (?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            Jdbc.setLong(ps, 1, v.eventId());
            Jdbc.setLong(ps, 2, v.transactionId());
            ps.setString(3, v.kind().name());
            ps.setString(4, v.message() == null ? "" : v.message());
            ps.setString(5, v.status().name());
            Jdbc.setInstant(ps, 6, v.queuedAt());
            Jdbc.setInstant(ps, 7, v.spokenAt());
            ps.setString(8, v.error());
            ps.executeUpdate();
            return Jdbc.generatedId(ps);
        }
    }

    public List<VoiceAnnouncement> findByEvent(long eventId) {
        return db.withConnection(c -> {
            List<VoiceAnnouncement> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM voice_announcement WHERE event_id = ? ORDER BY id")) {
                ps.setLong(1, eventId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new VoiceAnnouncement(rs.getLong("id"), Jdbc.getLong(rs, "event_id"),
                                Jdbc.getLong(rs, "transaction_id"), AnnouncementKind.valueOf(rs.getString("kind")),
                                rs.getString("message"), AnnouncementStatus.valueOf(rs.getString("status")),
                                Jdbc.getInstant(rs, "queued_at"), Jdbc.getInstant(rs, "spoken_at"), rs.getString("error")));
                    }
                }
            }
            return out;
        });
    }
}
