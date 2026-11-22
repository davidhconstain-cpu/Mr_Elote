package com.vivanetpay.ui.view;

import com.vivanetpay.reporting.DashboardSummary;
import com.vivanetpay.ui.Background;
import com.vivanetpay.ui.Ui;
import com.vivanetpay.ui.UiContext;
import com.vivanetpay.ui.viewmodel.DashboardViewModel;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Tablero (RF-013): confirmado y por verificar siempre separados. */
public final class DashboardScreen implements Screen {
    private final UiContext ctx;
    private final ScrollPane root = new ScrollPane();
    private final Label day = Ui.muted("");
    private final Label confirmedTotal = new Label();
    private final Label confirmedCount = Ui.muted("");
    private final Label unconfirmedTotal = new Label();
    private final Label unconfirmedCount = Ui.muted("");
    private final Label lastEvent = Ui.text("");
    private final Label lastSync = Ui.text("");
    private final Label pending = Ui.text("");
    private final Button goPending = new Button("Ir a Pendientes");
    private final TableView<DashboardViewModel.BankLine> banks = new TableView<>();
    private final TableView<DashboardViewModel.ConnectorLine> connectors = new TableView<>();

    public DashboardScreen(UiContext ctx) {
        this.ctx = ctx;
        confirmedTotal.getStyleClass().add("big-number");
        unconfirmedTotal.getStyleClass().add("big-number");
        goPending.setOnAction(e -> ctx.shell.navigate("pending"));
        Ui.placeholder(banks, "Aún no hay movimientos hoy");
        banks.getColumns().add(Ui.col("Entidad", 200, DashboardViewModel.BankLine::bank));
        banks.getColumns().add(Ui.col("Confirmado", 230, DashboardViewModel.BankLine::confirmed));
        banks.getColumns().add(Ui.col("Por verificar", 230, DashboardViewModel.BankLine::unconfirmed));
        banks.setPrefHeight(200);
        Ui.placeholder(connectors, "Sin conectores activos");
        connectors.getColumns().add(Ui.col("Conector", 160, DashboardViewModel.ConnectorLine::name));
        connectors.getColumns().add(Ui.col("Estado", 200, DashboardViewModel.ConnectorLine::status));
        connectors.getColumns().add(Ui.col("Detalle", 380, DashboardViewModel.ConnectorLine::detail));
        connectors.setPrefHeight(140);

        VBox confirmed = Ui.card("card-ok", Ui.heading("✔ Confirmado hoy"), confirmedTotal, confirmedCount,
                Ui.muted("Validado contra el canal oficial."));
        VBox unconfirmed = Ui.card("card-warn", Ui.heading("⚠ Por verificar hoy"), unconfirmedTotal, unconfirmedCount,
                Ui.muted("Posibles pagos: aún NO confirmados."));
        HBox cards = new HBox(14, confirmed, unconfirmed);
        HBox.setHgrow(confirmed, Priority.ALWAYS);
        HBox.setHgrow(unconfirmed, Priority.ALWAYS);
        confirmed.setMaxWidth(Double.MAX_VALUE);
        unconfirmed.setMaxWidth(Double.MAX_VALUE);

        VBox content = new VBox(12, Ui.title("Tablero"), day, cards,
                new HBox(10, pending, goPending),
                Ui.heading("Último evento"), lastEvent,
                Ui.heading("Desglose por entidad"), banks,
                Ui.heading("Conectores"), connectors,
                new HBox(6, Ui.heading("Última sincronización:"), lastSync));
        content.setPadding(new Insets(18));
        root.setContent(content);
        root.setFitToWidth(true);
    }

    @Override
    public String id() {
        return "dashboard";
    }

    @Override
    public String title() {
        return "Tablero";
    }

    @Override
    public Node node() {
        return root;
    }

    @Override
    public void refresh() {
        Background.run(() -> {
            DashboardSummary s = ctx.app.dashboardService().today();
            return DashboardViewModel.of(s, ctx.connectorStates());
        }, this::show, e -> ctx.shell.banner("No se pudo cargar el tablero: " + Ui.describe(e)));
    }

    private void show(DashboardViewModel vm) {
        day.setText("Hoy " + vm.dayText() + " (hora de Colombia)");
        confirmedTotal.setText(vm.confirmedTotal());
        confirmedCount.setText(vm.confirmedCount());
        unconfirmedTotal.setText(vm.unconfirmedTotal());
        unconfirmedCount.setText(vm.unconfirmedCount());
        pending.setText(vm.pendingText());
        goPending.setVisible(vm.hasPending());
        goPending.setManaged(vm.hasPending());
        lastEvent.setText(vm.lastEventText());
        lastSync.setText(vm.lastSyncText());
        banks.getItems().setAll(vm.banks());
        connectors.getItems().setAll(vm.connectors());
    }
}
