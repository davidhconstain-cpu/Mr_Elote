package com.vivanetpay.ui.viewmodel;

import com.vivanetpay.application.ReviewItem;
import com.vivanetpay.domain.Validation;
import com.vivanetpay.reporting.HistoryRow;
import com.vivanetpay.ui.Formats;

/** Lógica de la bandeja de Pendientes (Flujo D, RF-023). */
public final class ReviewViewModel {
    private ReviewViewModel() {
    }

    public record Row(long eventId, String when, String bank, String amount, String status, String reason,
                      ReviewItem item) {
    }

    public static Row row(ReviewItem item) {
        HistoryRow r = item.row();
        return new Row(r.eventId(), Formats.time(r.occurredAt()), r.bank() == null ? Formats.NONE : r.bank(),
                Formats.money(r.amount()), Formats.status(r.status()),
                Formats.friendlyReason(r.statusReason()), item);
    }

    /** @return el motivo recortado; lanza si es vacío (el descarte exige motivo). */
    public static String requireDiscardReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Debes escribir el motivo para descartar el evento.");
        }
        return reason.trim();
    }

    /** El evento está marcado como posible duplicado de otro (CP-014): la UI ofrece "Descartar como duplicado". */
    public static boolean isPossibleDuplicate(ReviewItem item) {
        return item.row().possibleDuplicateOfEventId() != null;
    }

    /** @return el motivo recortado; lanza si es vacío (confirmar un posible duplicado como pago distinto lo exige). */
    public static String requireDistinctPaymentReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Debes escribir por qué es un pago distinto para confirmarlo.");
        }
        return reason.trim();
    }

    /** Si el evento no tiene importe no puede confirmarse (DP-12). */
    public static boolean canValidate(ReviewItem item) {
        return item.row().amount() != null;
    }

    /** Panel de detalle: motivo de incertidumbre, datos extraídos, texto original permitido y validaciones. */
    public static String detail(ReviewItem item) {
        HistoryRow r = item.row();
        StringBuilder sb = new StringBuilder();
        sb.append("Por qué está pendiente: ")
                .append(r.statusReason() == null || r.statusReason().isBlank() ? "Requiere verificación manual" : r.statusReason())
                .append("\n\nDatos extraídos\n");
        sb.append("  Monto: ").append(Formats.money(r.amount())).append('\n');
        sb.append("  Entidad: ").append(r.bank() == null ? "No identificada" : r.bank()).append('\n');
        sb.append("  Remitente: ").append(r.sender() == null ? Formats.NONE : r.sender()).append('\n');
        sb.append("  Referencia: ").append(r.reference() == null ? Formats.NONE : r.reference()).append('\n');
        sb.append("  Fuente: ").append(Formats.source(r.source())).append('\n');
        sb.append("  Fecha: ").append(Formats.time(r.occurredAt())).append('\n');
        if (r.possibleDuplicateOfEventId() != null) {
            sb.append("  Posible duplicado del evento #").append(r.possibleDuplicateOfEventId()).append('\n');
        }
        sb.append("\nTexto original\n");
        if (item.rawSubject() != null && !item.rawSubject().isBlank()) {
            sb.append("  Asunto: ").append(item.rawSubject()).append('\n');
        }
        sb.append("  ").append(item.rawText() == null || item.rawText().isBlank() ? "(sin texto guardado)"
                : item.rawText().replace("\n", "\n  ")).append('\n');
        if (!item.validations().isEmpty()) {
            sb.append("\nValidaciones\n");
            for (Validation v : item.validations()) {
                sb.append("  - ").append(v.rule()).append(": ").append(v.result());
                if (v.reason() != null && !v.reason().isBlank()) {
                    sb.append(" (").append(v.reason()).append(')');
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }
}
