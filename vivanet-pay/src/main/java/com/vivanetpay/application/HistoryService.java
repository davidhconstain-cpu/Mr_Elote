package com.vivanetpay.application;

import com.vivanetpay.persistence.ReportingQueries;
import com.vivanetpay.reporting.HistoryFilter;
import com.vivanetpay.reporting.HistoryRow;

import java.util.List;
import java.util.Optional;

/** Consulta del historial con filtros (RF-014, RF-019). Llamar fuera del hilo de UI. */
public final class HistoryService {
    private final ReportingQueries queries;

    public HistoryService(ReportingQueries queries) {
        this.queries = queries;
    }

    public List<HistoryRow> search(HistoryFilter filter) {
        return queries.history(filter);
    }

    public Optional<HistoryRow> find(long eventId) {
        return queries.row(eventId);
    }
}
