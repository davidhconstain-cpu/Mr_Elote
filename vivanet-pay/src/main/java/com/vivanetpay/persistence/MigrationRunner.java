package com.vivanetpay.persistence;

import com.vivanetpay.validation.Fingerprint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Migraciones versionadas simples. Lee {@code db/migration/migrations.idx} (lista ordenada de archivos
 * {@code V<n>__<descripcion>.sql}) y aplica en una transacción cada versión aún no registrada en
 * {@code schema_version}. Verifica el checksum de las ya aplicadas (aviso si cambió).
 * <p>
 * Formato SQL admitido: sentencias terminadas en {@code ;} al final de línea; los bloques
 * {@code CREATE TRIGGER ... END;} se tratan como una sola sentencia; se ignoran líneas que empiezan con {@code --}.
 */
public final class MigrationRunner {
    private static final Logger log = LoggerFactory.getLogger(MigrationRunner.class);
    private static final String BASE = "db/migration/";
    private static final Pattern NAME = Pattern.compile("V(\\d+)__([A-Za-z0-9_]+)\\.sql");

    private final Database db;

    public MigrationRunner(Database db) {
        this.db = db;
    }

    /** Aplica las migraciones pendientes. @return versión final del esquema. */
    public int migrate() {
        db.withConnection(c -> {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS schema_version ("
                        + "version INTEGER PRIMARY KEY, description TEXT NOT NULL, checksum TEXT NOT NULL, "
                        + "applied_at INTEGER NOT NULL)");
            }
            return null;
        });
        Map<Integer, String> applied = appliedChecksums();
        int current = applied.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        for (String fileName : index()) {
            Matcher m = NAME.matcher(fileName);
            if (!m.matches()) {
                throw new PersistenceException("Nombre de migración inválido: " + fileName);
            }
            int version = Integer.parseInt(m.group(1));
            String description = m.group(2);
            String sql = read(fileName);
            String checksum = Fingerprint.sha256(sql);
            if (applied.containsKey(version)) {
                if (!applied.get(version).equals(checksum)) {
                    log.warn("La migración {} cambió después de aplicarse (checksum distinto)", fileName);
                }
                continue;
            }
            if (version <= current) {
                throw new PersistenceException("Migración fuera de orden: " + fileName);
            }
            db.inTransaction(c -> {
                try (Statement st = c.createStatement()) {
                    for (String stmt : split(sql)) {
                        st.execute(stmt);
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO schema_version(version, description, checksum, applied_at) VALUES (?,?,?,?)")) {
                    ps.setInt(1, version);
                    ps.setString(2, description);
                    ps.setString(3, checksum);
                    ps.setLong(4, System.currentTimeMillis());
                    ps.executeUpdate();
                }
                return null;
            });
            log.info("Migración aplicada: {}", fileName);
            current = version;
        }
        return current;
    }

    /** Versión actual del esquema (0 si no hay). */
    public int currentVersion() {
        return appliedChecksums().keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    private Map<Integer, String> appliedChecksums() {
        return db.withConnection(c -> {
            Map<Integer, String> map = new HashMap<>();
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT version, checksum FROM schema_version")) {
                while (rs.next()) {
                    map.put(rs.getInt(1), rs.getString(2));
                }
            } catch (SQLException e) {
                if (!String.valueOf(e.getMessage()).contains("no such table")) {
                    throw e;
                }
            }
            return map;
        });
    }

    private static List<String> index() {
        List<String> files = new ArrayList<>();
        for (String line : read("migrations.idx").split("\\R")) {
            String t = line.trim();
            if (!t.isEmpty() && !t.startsWith("#")) {
                files.add(t);
            }
        }
        return files;
    }

    private static String read(String name) {
        try (InputStream in = MigrationRunner.class.getClassLoader().getResourceAsStream(BASE + name)) {
            if (in == null) {
                throw new PersistenceException("Recurso de migración no encontrado: " + BASE + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new PersistenceException("No se pudo leer la migración " + name, e);
        }
    }

    /** Divide un script en sentencias. */
    static List<String> split(String sql) {
        List<String> out = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        boolean inTrigger = false;
        for (String line : sql.split("\\R")) {
            String t = line.trim();
            if (t.startsWith("--") || (t.isEmpty() && buf.isEmpty())) {
                continue;
            }
            if (buf.isEmpty() && t.toUpperCase(Locale.ROOT).startsWith("CREATE TRIGGER")) {
                inTrigger = true;
            }
            buf.append(line).append('\n');
            boolean end = inTrigger ? t.equalsIgnoreCase("END;") : t.endsWith(";");
            if (end) {
                out.add(buf.toString().trim());
                buf.setLength(0);
                inTrigger = false;
            }
        }
        if (!buf.toString().isBlank()) {
            out.add(buf.toString().trim());
        }
        return out;
    }

}
