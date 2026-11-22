package com.vivanetpay.persistence;

import java.sql.Connection;
import java.sql.SQLException;

/** Unidad de trabajo JDBC. */
@FunctionalInterface
public interface SqlWork<T> {
    T apply(Connection connection) throws SQLException;
}
