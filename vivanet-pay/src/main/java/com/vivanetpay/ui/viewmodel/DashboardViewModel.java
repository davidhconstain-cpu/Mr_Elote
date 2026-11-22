package com.vivanetpay.ui.viewmodel;

import com.vivanetpay.domain.ConnectorState;
import com.vivanetpay.reporting.BankBreakdown;
import com.vivanetpay.reporting.DashboardSummary;
import com.vivanetpay.reporting.HistoryRow;
import com.vivanetpay.ui.Formats;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Modelo de presentación del Tablero (RF-013), sin dependencias de JavaFX. Mantiene SEPARADOS el total
 * confirmado y el total "por verificar" para no presentar como confirmado lo que no lo está.
 */
public record DashboardViewModel(String dayText, String confirmedTotal, String confirmedCount,
                                 String unconfirmedTotal, String unconfirmedCount, String pendingText,
                                 boolean hasPending, String lastEventText, List<BankLine> banks,
                                 List<ConnectorLine> connectors, String lastSyncText) {

    public record BankLine(String bank, String confirmed, String unconfirmed) {
    }

    public record ConnectorLine(String name, String status, String detail) {
    }

    public static DashboardViewModel of(DashboardSummary s, List<ConnectorState> connectors) {
        List<BankLine> banks = s.byBank().stream()
                .sorted(Comparator.comparingLong((BankBreakdown b) -> b.confirmedTotal() + b.unconfirmedTotal()).reversed())
                .map(b -> new BankLine(b.bank(),
                        Formats.money(b.confirmedTotal()) + " (" + Formats.count(b.confirmedCount()) + ")",
                        Formats.money(b.unconfirmedTotal()) + " (" + Formats.count(b.unconfirmedCount()) + ")"))
                .toList();
        List<ConnectorLine> lines = connectors.stream()
                .map(c -> new ConnectorLine(c.type().name(), Formats.connectorStatus(c.status()), c.detail()))
                .toList();
        Instant lastSync = connectors.stream().map(ConnectorState::lastSuccessAt)
                .filter(java.util.Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
        return new DashboardViewModel(
                s.day().toString(),
                Formats.money(s.confirmedTotal()), Formats.count(s.confirmedCount()),
                Formats.money(s.unconfirmedTotal()), Formats.count(s.unconfirmedCount()),
                s.pendingReviewCount() == 0 ? "Sin pendientes"
                        : s.pendingReviewCount() + (s.pendingReviewCount() == 1 ? " pendiente de revisión" : " pendientes de revisión"),
                s.pendingReviewCount() > 0, lastEvent(s.lastMovement()), banks, lines,
                lastSync == null ? "Sin sincronizaciones aún" : Formats.time(lastSync));
    }

    static String lastEvent(HistoryRow r) {
        if (r == null) {
            return "Sin movimientos hoy";
        }
        return Formats.money(r.amount()) + " · " + (r.bank() == null ? "Entidad no identificada" : r.bank())
                + " · " + Formats.status(r.status()) + " · " + Formats.time(r.occurredAt());
    }
}
