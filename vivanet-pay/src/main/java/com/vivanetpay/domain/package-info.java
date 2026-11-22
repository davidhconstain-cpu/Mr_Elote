/**
 * Dominio de Vivanet Pay: entidades, estados y reglas de negocio puras (IDD §4.4, §7).
 * <p>
 * No depende de JavaFX, Gmail, MacroDroid ni SQLite. Los importes se representan como
 * {@code long} en pesos COP enteros (RN-006/RN-007) y los instantes como {@link java.time.Instant}
 * en UTC; la zona de presentación es {@link com.vivanetpay.domain.AppTime#BOGOTA} (RN-009).
 * Implementado por: agente núcleo.
 */
package com.vivanetpay.domain;
