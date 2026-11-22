package com.vivanetpay.application;

import com.vivanetpay.domain.AppTime;
import com.vivanetpay.persistence.ReportingQueries;
import com.vivanetpay.reporting.DashboardSummary;

import java.time.Clock;
import java.time.LocalDate;

/** Lectura del tablero (RF-013). Accede a la base: llamar fuera del hilo de UI. */
public final class DashboardService {
    private final ReportingQueries queries;
    private final Clock clock;

    public DashboardService(ReportingQueries queries, Clock clock) {
        this.queries = queries;
        this.clock = clock;
    }

    /** Resumen de "hoy" según la fecha local de Colombia (CP-015). */
    public DashboardSummary today() {
        return forDay(AppTime.localDate(clock.instant()));
    }

    public DashboardSummary forDay(LocalDate day) {
        return queries.dashboard(day, clock.instant());
    }
}
