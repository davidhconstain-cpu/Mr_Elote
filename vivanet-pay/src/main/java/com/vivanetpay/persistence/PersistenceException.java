package com.vivanetpay.persistence;

import java.sql.SQLException;

/** Error de persistencia no comprobado. */
public class PersistenceException extends RuntimeException {
    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }

    public PersistenceException(String message) {
        super(message);
    }

    /** @return true si la causa es una violación de UNIQUE (carrera de idempotencia). */
    public boolean isUniqueViolation() {
        return isUniqueViolation(getCause());
    }

    public static boolean isUniqueViolation(Throwable t) {
        while (t != null) {
            if (t instanceof SQLException se) {
                String msg = se.getMessage() == null ? "" : se.getMessage();
                if (msg.contains("UNIQUE constraint failed") || msg.contains("SQLITE_CONSTRAINT_UNIQUE")
                        || msg.contains("SQLITE_CONSTRAINT_PRIMARYKEY")) {
                    return true;
                }
            }
            t = t.getCause();
        }
        return false;
    }
}
