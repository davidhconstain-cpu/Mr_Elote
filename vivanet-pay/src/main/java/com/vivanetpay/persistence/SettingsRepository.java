package com.vivanetpay.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Preferencias no secretas ({@code app_setting}). Para secretos, guardar en {@code SecretStore} y aquí solo la
 * referencia con {@link #putSecretRef}.
 */
public final class SettingsRepository {
    private final Database db;
    private final Clock clock;

    public SettingsRepository(Database db, Clock clock) {
        this.db = db;
        this.clock = clock;
    }

    public Optional<String> get(String key) {
        return db.withConnection(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT value FROM app_setting WHERE key = ?")) {
                ps.setString(1, key);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.ofNullable(rs.getString(1)) : Optional.empty();
                }
            }
        });
    }

    public void put(String key, String value) {
        put(key, value, false);
    }

    /** Guarda la clave del SecretStore donde vive un secreto (nunca el secreto). */
    public void putSecretRef(String key, String secretStoreKey) {
        put(key, secretStoreKey, true);
    }

    private void put(String key, String value, boolean secretRef) {
        db.withConnection(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO app_setting (key, value, is_secret_ref, updated_at) "
                    + "VALUES (?,?,?,?) ON CONFLICT(key) DO UPDATE SET value = excluded.value, "
                    + "is_secret_ref = excluded.is_secret_ref, updated_at = excluded.updated_at")) {
                ps.setString(1, key);
                ps.setString(2, value);
                ps.setInt(3, secretRef ? 1 : 0);
                Jdbc.setInstant(ps, 4, clock.instant());
                return ps.executeUpdate();
            }
        });
    }

    public Map<String, String> all() {
        return db.withConnection(c -> {
            Map<String, String> out = new LinkedHashMap<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT key, value FROM app_setting ORDER BY key");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getString(1), rs.getString(2));
                }
            }
            return out;
        });
    }
}
