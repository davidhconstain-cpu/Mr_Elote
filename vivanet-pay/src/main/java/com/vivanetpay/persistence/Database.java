package com.vivanetpay.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Acceso a la base SQLite local. Abre una conexión por unidad de trabajo (SQLite es local y barato), con:
 * WAL, {@code foreign_keys=ON}, {@code synchronous=FULL} (resistencia a apagado abrupto, CP-012),
 * {@code busy_timeout} y transacciones IMMEDIATE (un escritor a la vez, sin interbloqueos de escalamiento).
 * Seguro para hilos.
 */
public final class Database {
    private static final Logger log = LoggerFactory.getLogger(Database.class);

    private final Path file;
    private final String url;
    private final SQLiteConfig config;

    private Database(Path file) {
        this.file = file.toAbsolutePath();
        this.url = "jdbc:sqlite:" + this.file;
        this.config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setSynchronous(SQLiteConfig.SynchronousMode.FULL);
        config.setBusyTimeout(10_000);
        config.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE);
        config.setEncoding(SQLiteConfig.Encoding.UTF8);
    }

    /** Abre (creando el archivo y directorios si no existen). No aplica migraciones. */
    public static Database open(Path file) {
        Objects.requireNonNull(file, "file");
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            throw new PersistenceException("No se pudo crear el directorio de la base de datos", e);
        }
        Database db = new Database(file);
        db.withConnection(c -> null); // valida que abre
        log.info("Base de datos abierta: {}", db.file.getFileName());
        return db;
    }

    /** Abre y migra al esquema más reciente. */
    public static Database openAndMigrate(Path file) {
        Database db = open(file);
        new MigrationRunner(db).migrate();
        return db;
    }

    public Path file() {
        return file;
    }

    /** Nueva conexión configurada (autocommit activo). El llamador debe cerrarla. */
    public Connection connect() throws SQLException {
        return DriverManager.getConnection(url, config.toProperties());
    }

    /** Ejecuta en una conexión con autocommit (lecturas o escrituras simples). */
    public <T> T withConnection(SqlWork<T> work) {
        try (Connection c = connect()) {
            return work.apply(c);
        } catch (SQLException e) {
            throw new PersistenceException("Error de base de datos: " + e.getMessage(), e);
        }
    }

    /**
     * Ejecuta en una transacción IMMEDIATE: todo o nada. Cualquier excepción provoca rollback y se relanza
     * (las {@link SQLException} envueltas en {@link PersistenceException}).
     */
    public <T> T inTransaction(SqlWork<T> work) {
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            try {
                T result = work.apply(c);
                c.commit();
                return result;
            } catch (SQLException | RuntimeException | Error e) {
                try {
                    c.rollback();
                } catch (SQLException re) {
                    e.addSuppressed(re);
                }
                if (e instanceof SQLException se) {
                    throw new PersistenceException("Error de base de datos: " + se.getMessage(), se);
                }
                throw e;
            }
        } catch (SQLException e) {
            throw new PersistenceException("Error de base de datos: " + e.getMessage(), e);
        }
    }
}
