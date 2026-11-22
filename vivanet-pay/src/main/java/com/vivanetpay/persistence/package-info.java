/**
 * Persistencia local SQLite vía JDBC puro (IDD §7, RF-012, RNF-006, RNF-013).
 * <p>
 * {@link com.vivanetpay.persistence.Database} abre conexiones con WAL, foreign_keys=ON, synchronous=FULL y
 * transacciones IMMEDIATE; {@link com.vivanetpay.persistence.MigrationRunner} aplica
 * {@code resources/db/migration/V*.sql} según {@code migrations.idx}; los repositorios reciben la
 * {@link java.sql.Connection} de la transacción en curso para escribir de forma atómica.
 * Timestamps = epoch ms UTC; importes = INTEGER pesos. Sin borrado físico (RN-008, triggers).
 * Implementado por: agente núcleo.
 */
package com.vivanetpay.persistence;
