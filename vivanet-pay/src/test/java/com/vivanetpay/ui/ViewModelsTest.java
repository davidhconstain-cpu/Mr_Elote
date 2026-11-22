package com.vivanetpay.ui;

import com.vivanetpay.domain.ConnectorState;
import com.vivanetpay.domain.ConnectorStatus;
import com.vivanetpay.domain.ConnectorType;
import com.vivanetpay.domain.EventSource;
import com.vivanetpay.domain.EventStatus;
import com.vivanetpay.integration.AuthorizableConnector;
import com.vivanetpay.integration.Connector;
import com.vivanetpay.integration.ConnectorStateListener;
import com.vivanetpay.reporting.BankBreakdown;
import com.vivanetpay.reporting.DashboardSummary;
import com.vivanetpay.reporting.HistoryFilter;
import com.vivanetpay.ui.viewmodel.ConnectorsViewModel;
import com.vivanetpay.ui.viewmodel.DashboardViewModel;
import com.vivanetpay.ui.viewmodel.HistoryViewModel;
import com.vivanetpay.ui.viewmodel.ReviewViewModel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ViewModelsTest {

    @Test
    void formatsAmountsWithoutDecimals() {
        assertThat(Formats.money(125000)).isEqualTo("$125.000");
        assertThat(Formats.money(1250000L)).isEqualTo("$1.250.000");
        assertThat(Formats.money((Long) null)).isEqualTo("Sin importe");
        assertThat(Formats.time(Instant.parse("2025-03-01T03:30:00Z"))).isEqualTo("28/02/2025 22:30:00"); // Bogotá UTC-5
    }

    @Test
    void dashboardKeepsConfirmedAndUnconfirmedSeparate() {
        DashboardSummary s = new DashboardSummary(LocalDate.of(2025, 3, 1), 300000, 2, 50000, 1, 3,
                List.of(new BankBreakdown("Nequi", 100000, 1, 50000, 1), new BankBreakdown("Bancolombia", 200000, 1, 0, 0)),
                UiTestData.row(1, "Nequi", 50000L, "x", EventStatus.PARSEADO), Instant.parse("2025-03-01T20:00:00Z"));
        ConnectorState c = new ConnectorState("g", ConnectorType.GMAIL, ConnectorStatus.CONECTADO, "ok",
                Instant.parse("2025-03-01T19:00:00Z"), Instant.parse("2025-03-01T19:00:00Z"));
        DashboardViewModel vm = DashboardViewModel.of(s, List.of(c));
        assertThat(vm.confirmedTotal()).isEqualTo("$300.000");
        assertThat(vm.confirmedCount()).isEqualTo("2 movimientos");
        assertThat(vm.unconfirmedTotal()).isEqualTo("$50.000");
        assertThat(vm.unconfirmedCount()).isEqualTo("1 movimiento");
        assertThat(vm.hasPending()).isTrue();
        assertThat(vm.pendingText()).isEqualTo("3 pendientes de revisión");
        assertThat(vm.banks().get(0).bank()).isEqualTo("Bancolombia"); // ordenado por total
        assertThat(vm.lastEventText()).contains("$50.000").contains("Por verificar");
        assertThat(vm.lastSyncText()).isEqualTo("01/03/2025 14:00:00");
        assertThat(vm.connectors().get(0).status()).contains("Conectado");
    }

    @Test
    void historyFilterParsesAmountsAndGroups() {
        HistoryFilter f = HistoryViewModel.toFilter(new HistoryViewModel.FilterInput(LocalDate.of(2025, 3, 1),
                LocalDate.of(2025, 3, 31), EventSource.GMAIL, "CONFIRMADOS", "$10.000", "250000", "  ana "));
        assertThat(f.minAmount()).isEqualTo(10000L);
        assertThat(f.maxAmount()).isEqualTo(250000L);
        assertThat(f.sources()).containsExactly(EventSource.GMAIL);
        assertThat(f.statuses()).isEqualTo(EventStatus.confirmedStatuses());
        assertThat(f.text()).isEqualTo("ana");
        HistoryFilter v = HistoryViewModel.toFilter(new HistoryViewModel.FilterInput(null, null, null, "POR_VERIFICAR", "", null, null));
        assertThat(v.statuses()).isEqualTo(Set.of(EventStatus.PARSEADO, EventStatus.PENDIENTE_REVISION));
        assertThat(v.minAmount()).isNull();
        HistoryFilter one = HistoryViewModel.toFilter(new HistoryViewModel.FilterInput(null, null, null, "DUPLICADO", null, null, null));
        assertThat(one.statuses()).containsExactly(EventStatus.DUPLICADO);
    }

    @Test
    void historyFilterRejectsBadInput() {
        assertThatThrownBy(() -> HistoryViewModel.toFilter(new HistoryViewModel.FilterInput(LocalDate.of(2025, 4, 1),
                LocalDate.of(2025, 3, 1), null, null, null, null, null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HistoryViewModel.toFilter(new HistoryViewModel.FilterInput(null, null, null, null,
                "abc", null, null))).hasMessageContaining("mínimo");
        assertThatThrownBy(() -> HistoryViewModel.toFilter(new HistoryViewModel.FilterInput(null, null, null, null,
                "500", "100", null))).hasMessageContaining("mayor");
    }

    @Test
    void historyRowAndDetail() {
        var r = UiTestData.row(7, null, 125000L, null, EventStatus.VALIDADO_POR_FUENTE);
        HistoryViewModel.Row row = HistoryViewModel.row(r);
        assertThat(row.amount()).isEqualTo("$125.000");
        assertThat(row.bank()).isEqualTo(Formats.NONE);
        assertThat(row.status()).contains("Confirmado");
        assertThat(HistoryViewModel.detail(r)).contains("Evento #7").contains("No identificada");
    }

    @Test
    void discardRequiresReason() {
        assertThatThrownBy(() -> ReviewViewModel.requireDiscardReason("  ")).isInstanceOf(IllegalArgumentException.class);
        assertThat(ReviewViewModel.requireDiscardReason(" no llegó ")).isEqualTo("no llegó");
    }

    @Test
    void reviewDetailShowsOriginalTextAndCannotValidateWithoutAmount() {
        var item = new com.vivanetpay.application.ReviewItem(UiTestData.row(3, "Nequi", null, "x", EventStatus.PENDIENTE_REVISION),
                "Asunto", "Texto original\nlinea 2", List.of());
        assertThat(ReviewViewModel.canValidate(item)).isFalse();
        assertThat(ReviewViewModel.detail(item)).contains("Texto original").contains("Asunto: Asunto").contains("Sin importe");
    }

    public static class Fake implements Connector {
        final ConnectorState st = new ConnectorState("f", ConnectorType.GMAIL, ConnectorStatus.REQUIERE_AUTORIZACION, "d",
                null, Instant.EPOCH);

        @Override public String id() { return "f"; }
        @Override public ConnectorType type() { return ConnectorType.GMAIL; }
        @Override public void start() { }
        @Override public void stop() { }
        @Override public void pause() { }
        @Override public void resume() { }
        @Override public ConnectorState state() { return st; }
        @Override public void addStateListener(ConnectorStateListener l) { }
        @Override public void removeStateListener(ConnectorStateListener l) { }
    }

    public static class Auth extends Fake implements AuthorizableConnector {
        @Override public void authorize() { }
        @Override public void revoke() { }
        @Override public boolean isAuthorized() { return false; }
        @Override public java.util.Optional<String> authorizedAccount() { return java.util.Optional.empty(); }
        public String testReception() { return "recibido"; }
    }

    @Test
    void connectorCapabilitiesAreDiscovered() throws Exception {
        ConnectorsViewModel plain = ConnectorsViewModel.of(new Fake());
        assertThat(plain.canAuthorize()).isFalse();
        assertThat(plain.canTestReception()).isFalse();
        assertThat(plain.status()).contains("Requiere autorización");
        assertThat(plain.action()).contains("Autorizar");
        Auth a = new Auth();
        ConnectorsViewModel vm = ConnectorsViewModel.of(a);
        assertThat(vm.canAuthorize()).isTrue();
        assertThat(vm.canTestReception()).isTrue();
        assertThat(ConnectorsViewModel.runTest(a)).isEqualTo("recibido");
    }
}
