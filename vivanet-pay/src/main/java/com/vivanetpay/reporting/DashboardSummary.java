package com.vivanetpay.reporting;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Resumen del tablero para un día local de Colombia (RF-013). Separa confirmados de posibles pagos por
 * verificar para no presentar como confirmado lo que no lo está (RF-011). Excluye DUPLICADO, RECHAZADO y ERROR.
 *
 * @param day                día local (America/Bogota)
 * @param confirmedTotal     pesos confirmados
 * @param confirmedCount     cantidad confirmados
 * @param unconfirmedTotal   pesos por verificar
 * @param unconfirmedCount   cantidad por verificar
 * @param pendingReviewCount eventos en bandeja de revisión (todas las fechas; incluye sin importe)
 * @param byBank             desglose por entidad
 * @param lastMovement       último movimiento del día (nullable)
 * @param generatedAt        instante de cálculo
 */
public record DashboardSummary(LocalDate day, long confirmedTotal, int confirmedCount, long unconfirmedTotal,
                               int unconfirmedCount, int pendingReviewCount, List<BankBreakdown> byBank,
                               HistoryRow lastMovement, Instant generatedAt) {
    public DashboardSummary {
        byBank = List.copyOf(byBank);
    }

    /** Total del día (confirmados + por verificar). */
    public long totalAll() {
        return confirmedTotal + unconfirmedTotal;
    }

    public int countAll() {
        return confirmedCount + unconfirmedCount;
    }
}
