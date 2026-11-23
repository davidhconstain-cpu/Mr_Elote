package com.vivanetpay.ui.view;

import com.vivanetpay.reporting.DashboardSummary;
import com.vivanetpay.ui.Background;
import com.vivanetpay.ui.Ui;
import com.vivanetpay.ui.UiContext;
import com.vivanetpay.ui.viewmodel.DashboardModel;
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
    private final DashboardModel model = new DashboardModel();
    private final Label day = Ui.muted("");
    private final Label confirmedTotal = new Label();
    private final Label confirmedCount = Ui.muted("");
    private final Label unconfirmedTotal = new Label();
    private final Label unconfirmedCount = Ui.muted("");
    private final Label lastEvent = Ui.text("");
    private final Label lastSync = Ui.text("");
    private final Label pending = Ui.text("");
    private final Button goPending = new Button("Ir a Pendientes");
    private final TableView<DashboardViewModel.BankLine> banks = Ui.table("Desglose por entidad", "Aún no hay movimientos hoy");
    private final TableView<DashboardViewModel.ConnectorLine> connectors = Ui.table("Estado de los conectores", "Sin conectores activos");

    public DashboardScreen(UiContext ctx) {
        this.ctx = ctx;
        confirmedTotal.getStyleClass().add("big-number");
        unconfirmedTotal.getStyleClass().add("big-number");
        goPending.setOnAction(e -> ctx.shell.navigate("pending"));
        banks.getColumns().add(Ui.col("Entidad", 200, DashboardViewModel.BankLine::bank));
        banks.getColumns().add(Ui.col("Confirmado", 230, DashboardViewModel.BankLine::confirmed));
        banks.getColumns().add(Ui.col("Por verificar", 230, DashboardViewModel.BankLine::unconfirmed));
        banks.setPrefHeight(200);
        connectors.getColumns().add(Ui.col("Conector", 160, DashboardViewModel.ConnectorLine::name));
        connectors.getColumns().add(Ui.col("Estado", 200, DashboardViewModel.ConnectorLine::status));
        connectors.getColumns().add(Ui.col("Detalle", 380, DashboardViewModel.ConnectorLine::detail));
        connectors.setPrefHeight(140);
        bindModel();

        VBox confirmed = Ui.card("card-ok", Ui.heading("✔ Confirmado hoy"), confirmedTotal, confirmedCount,
                Ui.muted("Confirmados manualmente tras revisar el canal oficial."));
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
        }, model::update, e -> ctx.shell.banner("No se pudo cargar el tablero: " + Ui.describe(e)));
    }

    /** Los controles se enlazan una vez al modelo observable; refresh() solo entrega datos nuevos. */
    private void bindModel() {
        day.textProperty().bind(model.day());
        confirmedTotal.textProperty().bind(model.text(DashboardViewModel::confirmedTotal, ""));
        confirmedCount.textProperty().bind(model.text(DashboardViewModel::confirmedCount, ""));
        unconfirmedTotal.textProperty().bind(model.text(DashboardViewModel::unconfirmedTotal, ""));
        unconfirmedCount.textProperty().bind(model.text(DashboardViewModel::unconfirmedCount, ""));
        pending.textProperty().bind(model.text(DashboardViewModel::pendingText, ""));
        lastEvent.textProperty().bind(model.text(DashboardViewModel::lastEventText, ""));
        lastSync.textProperty().bind(model.text(DashboardViewModel::lastSyncText, ""));
        goPending.visibleProperty().bind(model.hasPending());
        goPending.managedProperty().bind(goPending.visibleProperty());
        banks.setItems(model.banks());
        connectors.setItems(model.connectors());
        // Lectores de pantalla: los importes grandes se leen con su significado, no como un número suelto.
        confirmedTotal.accessibleTextProperty().bind(model.text(vm -> "Confirmado hoy: " + vm.confirmedTotal()
                + ", " + vm.confirmedCount(), ""));
        unconfirmedTotal.accessibleTextProperty().bind(model.text(vm -> "Por verificar hoy, no confirmado: "
                + vm.unconfirmedTotal() + ", " + vm.unconfirmedCount(), ""));
    }
}
