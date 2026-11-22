package com.vivanetpay.reporting;

import com.vivanetpay.domain.EventSource;
import com.vivanetpay.domain.EventStatus;

import java.time.LocalDate;
import java.util.Set;

/**
 * Filtros del historial (RF-019). Todos opcionales (null/vacío = sin filtro).
 *
 * @param fromDate  día local inicial (inclusive, America/Bogota)
 * @param toDate    día local final (inclusive)
 * @param sources   fuentes
 * @param statuses  estados
 * @param minAmount importe mínimo en pesos (inclusive)
 * @param maxAmount importe máximo en pesos (inclusive)
 * @param bank      entidad exacta (sin distinguir mayúsculas)
 * @param text      búsqueda en remitente/referencia
 * @param limit     máximo de filas (por defecto 500)
 * @param offset    desplazamiento para paginación
 */
public record HistoryFilter(LocalDate fromDate, LocalDate toDate, Set<EventSource> sources, Set<EventStatus> statuses,
                            Long minAmount, Long maxAmount, String bank, String text, int limit, int offset) {
    public static final int DEFAULT_LIMIT = 500;

    public HistoryFilter {
        sources = sources == null ? Set.of() : Set.copyOf(sources);
        statuses = statuses == null ? Set.of() : Set.copyOf(statuses);
        if (limit <= 0) {
            limit = DEFAULT_LIMIT;
        }
        if (offset < 0) {
            offset = 0;
        }
    }

    public static HistoryFilter all() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private LocalDate fromDate;
        private LocalDate toDate;
        private Set<EventSource> sources = Set.of();
        private Set<EventStatus> statuses = Set.of();
        private Long minAmount;
        private Long maxAmount;
        private String bank;
        private String text;
        private int limit = DEFAULT_LIMIT;
        private int offset;

        public Builder from(LocalDate v) { this.fromDate = v; return this; }
        public Builder to(LocalDate v) { this.toDate = v; return this; }
        public Builder sources(Set<EventSource> v) { this.sources = v; return this; }
        public Builder statuses(Set<EventStatus> v) { this.statuses = v; return this; }
        public Builder minAmount(Long v) { this.minAmount = v; return this; }
        public Builder maxAmount(Long v) { this.maxAmount = v; return this; }
        public Builder bank(String v) { this.bank = v; return this; }
        public Builder text(String v) { this.text = v; return this; }
        public Builder limit(int v) { this.limit = v; return this; }
        public Builder offset(int v) { this.offset = v; return this; }

        public HistoryFilter build() {
            return new HistoryFilter(fromDate, toDate, sources, statuses, minAmount, maxAmount, bank, text, limit, offset);
        }
    }
}
