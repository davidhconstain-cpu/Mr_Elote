package com.vivanetpay.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;

/** Utilidades JDBC: conversión Instant <-> epoch ms UTC y nulos. */
final class Jdbc {
    private Jdbc() {
    }

    static void setInstant(PreparedStatement ps, int idx, Instant v) throws SQLException {
        if (v == null) {
            ps.setNull(idx, Types.INTEGER);
        } else {
            ps.setLong(idx, v.toEpochMilli());
        }
    }

    static void setLong(PreparedStatement ps, int idx, Long v) throws SQLException {
        if (v == null) {
            ps.setNull(idx, Types.INTEGER);
        } else {
            ps.setLong(idx, v);
        }
    }

    static void setDouble(PreparedStatement ps, int idx, Double v) throws SQLException {
        if (v == null) {
            ps.setNull(idx, Types.REAL);
        } else {
            ps.setDouble(idx, v);
        }
    }

    static Instant getInstant(ResultSet rs, String col) throws SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : Instant.ofEpochMilli(v);
    }

    static Long getLong(ResultSet rs, String col) throws SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }

    static Double getDouble(ResultSet rs, String col) throws SQLException {
        double v = rs.getDouble(col);
        return rs.wasNull() ? null : v;
    }

    static long generatedId(Statement st) throws SQLException {
        try (ResultSet keys = st.getGeneratedKeys()) {
            if (keys.next()) {
                return keys.getLong(1);
            }
        }
        throw new SQLException("No se obtuvo id generado");
    }
}
