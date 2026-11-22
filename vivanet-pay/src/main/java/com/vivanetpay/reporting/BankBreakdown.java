package com.vivanetpay.reporting;

/**
 * Desglose del día por entidad.
 *
 * @param bank             entidad ("Sin identificar" si no se extrajo)
 * @param confirmedTotal   pesos confirmados (VALIDADO_POR_FUENTE/ANUNCIADO)
 * @param confirmedCount   movimientos confirmados
 * @param unconfirmedTotal pesos de posibles pagos por verificar (PARSEADO/PENDIENTE_REVISION)
 * @param unconfirmedCount movimientos por verificar
 */
public record BankBreakdown(String bank, long confirmedTotal, int confirmedCount,
                            long unconfirmedTotal, int unconfirmedCount) {
}
