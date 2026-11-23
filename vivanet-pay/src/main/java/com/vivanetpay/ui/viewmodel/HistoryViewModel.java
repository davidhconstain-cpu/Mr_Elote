package com.vivanetpay.ui.viewmodel;

import com.vivanetpay.domain.EventSource;
import com.vivanetpay.domain.EventStatus;
import com.vivanetpay.reporting.HistoryFilter;
import com.vivanetpay.reporting.HistoryRow;
import com.vivanetpay.ui.Formats;

import java.time.LocalDate;
import java.util.Set;

/** Lógica del Historial (RF-014, RF-019): construcción de filtros desde la entrada del usuario y detalle. */
public final class HistoryViewModel {
    private HistoryViewModel() {
    }

    /**
     * Entrada cruda de los filtros de la pantalla.
     *
     * @param statusGroup null = todos; "CONFIRMADOS", "POR_VERIFICAR" o un nombre de {@link EventStatus}
     */
    public record FilterInput(LocalDate from, LocalDate to, EventSource source, String statusGroup,
                              String minAmountText, String maxAmountText, String text) {
    }

    /** @throws IllegalArgumentException con mensaje en español si la entrada es inválida */
    public static HistoryFilter toFilter(FilterInput in) {
        if (in.from() != null && in.to() != null && in.from().isAfter(in.to())) {
            throw new IllegalArgumentException("La fecha inicial no puede ser posterior a la final.");
        }
        Long min = parseAmount(in.minAmountText(), "mínimo");
        Long max = parseAmount(in.maxAmountText(), "máximo");
        if (min != null && max != null && min > max) {
            throw new IllegalArgumentException("El monto mínimo no puede ser mayor que el máximo.");
        }
        Set<EventStatus> statuses = Set.of();
        String g = in.statusGroup();
        if (g != null && !g.isBlank()) {
            statuses = switch (g) {
                case "CONFIRMADOS" -> EventStatus.confirmedStatuses();
                case "POR_VERIFICAR" -> EventStatus.unconfirmedStatuses();
                default -> Set.of(EventStatus.valueOf(g));
            };
        }
        return HistoryFilter.builder().from(in.from()).to(in.to())
                .sources(in.source() == null ? Set.of() : Set.of(in.source()))
                .statuses(statuses).minAmount(min).maxAmount(max)
                .text(in.text() == null || in.text().isBlank() ? null : in.text().trim()).build();
    }

    /** Acepta "125000", "$125.000", "125.000": solo cuenta los dígitos. Vacío = sin filtro. */
    public static Long parseAmount(String text, String what) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String t = text.trim();
        if (!t.matches("[$\\s.,0-9]+")) {
            throw new IllegalArgumentException("El monto " + what + " debe contener solo números (ejemplo: $125.000).");
        }
        String digits = t.replaceAll("\\D", "");
        if (digits.isEmpty() || digits.length() > 12) {
            throw new IllegalArgumentException("El monto " + what + " no es válido.");
        }
        return Long.parseLong(digits);
    }

    /** Resumen bajo los filtros: cantidad de resultados y aviso si se alcanzó el límite. */
    public static String summary(int shown, int limit) {
        return shown + " resultado(s)"
                + (shown >= limit ? " (se muestran los primeros " + limit + "; afina los filtros)" : "")
                + ". Horas en America/Bogota.";
    }

    /** Fila de la tabla ya formateada. */
    public record Row(long eventId, String when, String source, String bank, String amount, String sender,
                      String status, HistoryRow raw) {
    }

    public static Row row(HistoryRow r) {
        return new Row(r.eventId(), Formats.time(r.occurredAt()), Formats.source(r.source()),
                r.bank() == null ? Formats.NONE : r.bank(), Formats.money(r.amount()),
                r.sender() == null ? Formats.NONE : r.sender(), Formats.status(r.status()), r);
    }

    /** Texto del panel de detalle. */
    public static String detail(HistoryRow r) {
        StringBuilder sb = new StringBuilder();
        sb.append("Evento #").append(r.eventId()).append('\n');
        sb.append("Estado: ").append(Formats.status(r.status())).append('\n');
        if (r.statusReason() != null && !r.statusReason().isBlank()) {
            sb.append("Motivo: ").append(r.statusReason()).append('\n');
        }
        sb.append("Monto: ").append(Formats.money(r.amount())).append('\n');
        sb.append("Entidad: ").append(r.bank() == null ? "No identificada" : r.bank()).append('\n');
        sb.append("Remitente: ").append(r.sender() == null ? Formats.NONE : r.sender()).append('\n');
        sb.append("Referencia: ").append(r.reference() == null ? Formats.NONE : r.reference()).append('\n');
        sb.append("Fuente: ").append(Formats.source(r.source())).append(" (").append(r.connectorId()).append(")\n");
        sb.append("Fecha del pago: ").append(Formats.time(r.occurredAt())).append('\n');
        sb.append("Recibido: ").append(Formats.time(r.receivedAt())).append('\n');
        if (r.duplicateOfEventId() != null) {
            sb.append("Duplicado del evento #").append(r.duplicateOfEventId()).append('\n');
        }
        if (r.possibleDuplicateOfEventId() != null) {
            sb.append("Posible duplicado del evento #").append(r.possibleDuplicateOfEventId()).append('\n');
        }
        return sb.toString();
    }
}
