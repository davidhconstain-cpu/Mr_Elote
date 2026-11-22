package com.vivanetpay.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Respaldo y verificación de integridad (RNF-013, IDD §8.5).
 * <p>
 * Usa la API de backup en línea de SQLite (comando {@code backup to} del driver xerial), que produce una copia
 * consistente aunque haya escrituras en curso. Frecuencia, retención, cifrado y ubicación externa de copias
 * están PENDIENTES de aprobación (DP-05/DP-10); una copia en el mismo disco no protege ante pérdida del equipo.
 * <p>
 * Procedimiento de restauración documentado: cerrar la aplicación (o detener conectores), llamar a
 * {@link #restoreFrom(Path)} con una copia que pase {@link #integrityCheck(Path)}, y reiniciar.
 */
public final class DatabaseMaintenance {
    private static final Logger log = LoggerFactory.getLogger(DatabaseMaintenance.class);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final Database db;
    private final Clock clock;

    public DatabaseMaintenance(Database db, Clock clock) {
        this.db = db;
        this.clock = clock;
    }

    /** PRAGMA integrity_check de la base activa. @return lista vacía si está íntegra; si no, los problemas. */
    public List<String> integrityCheck() {
        return db.withConnection(DatabaseMaintenance::integrity);
    }

    /** Verifica la integridad de un archivo de respaldo sin abrirlo como base activa. */
    public static List<String> integrityCheck(Path file) {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath())) {
            return integrity(c);
        } catch (SQLException e) {
            return List.of("No se pudo abrir el respaldo: " + e.getMessage());
        }
    }

    private static List<String> integrity(Connection c) throws SQLException {
        List<String> problems = new ArrayList<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("PRAGMA integrity_check")) {
            while (rs.next()) {
                String r = rs.getString(1);
                if (!"ok".equalsIgnoreCase(r)) {
                    problems.add(r);
                }
            }
        }
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("PRAGMA foreign_key_check")) {
            while (rs.next()) {
                problems.add("foreign_key_check: tabla " + rs.getString(1) + " fila " + rs.getLong(2));
            }
        }
        return problems;
    }

    /** Respalda a un archivo concreto (no debe existir). */
    public Path backupTo(Path target) {
        try {
            Path parent = target.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (Files.exists(target)) {
                throw new PersistenceException("El archivo de respaldo ya existe: " + target.getFileName());
            }
        } catch (IOException e) {
            throw new PersistenceException("No se pudo preparar el respaldo", e);
        }
        db.withConnection(c -> {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("backup to " + quote(target));
            }
            return null;
        });
        List<String> problems = integrityCheck(target);
        if (!problems.isEmpty()) {
            throw new PersistenceException("El respaldo no pasó integrity_check: " + problems);
        }
        log.info("Respaldo creado: {}", target.getFileName());
        return target;
    }

    /** Respalda en {@code dir} con nombre {@code vivanetpay-AAAAMMDD-HHMMSS.db} (UTC). */
    public Path backupToDirectory(Path dir) {
        return backupTo(dir.resolve("vivanetpay-" + STAMP.format(clock.instant()) + ".db"));
    }

    /**
     * Restaura la base activa desde un respaldo verificado. Detener conectores antes de llamar.
     *
     * @throws PersistenceException si el respaldo no es íntegro
     */
    public void restoreFrom(Path backup) {
        if (!Files.isRegularFile(backup)) {
            throw new PersistenceException("Respaldo no encontrado: " + backup.getFileName());
        }
        List<String> problems = integrityCheck(backup);
        if (!problems.isEmpty()) {
            throw new PersistenceException("El respaldo no es íntegro, no se restaura: " + problems);
        }
        db.withConnection(c -> {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("restore from " + quote(backup));
            }
            return null;
        });
        List<String> after = integrityCheck();
        if (!after.isEmpty()) {
            throw new PersistenceException("La base restaurada no pasó integrity_check: " + after);
        }
        log.warn("Base de datos restaurada desde {}", backup.getFileName());
    }

    private static String quote(Path p) {
        String s = p.toAbsolutePath().toString();
        if (s.contains("'")) {
            throw new PersistenceException("Ruta de respaldo no admitida (contiene comilla simple)");
        }
        return "'" + s + "'";
    }
}
