package com.vivanetpay.ui.view;

import com.vivanetpay.application.ReviewItem;
import com.vivanetpay.ui.Background;
import com.vivanetpay.ui.Ui;
import com.vivanetpay.ui.UiContext;
import com.vivanetpay.ui.viewmodel.ReviewViewModel;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Optional;

/** Pendientes (Flujo D, RF-023): Validar / Descartar (motivo obligatorio) / Mantener. */
public final class PendingScreen implements Screen {
    private final UiContext ctx;
    private final VBox root = new VBox(10);
    private final TableView<ReviewViewModel.Row> table = new TableView<>();
    private final TextArea detail = new TextArea();
    private final Button validate = new Button("✔ Validar (ya lo vi en mi banco)");
    private final Button discard = new Button("✖ Descartar…");
    private final Button keep = new Button("⏸ Mantener pendiente");

    public PendingScreen(UiContext ctx) {
        this.ctx = ctx;
        Ui.placeholder(table, "No hay eventos pendientes de revisión");
        table.getColumns().add(Ui.col("Fecha y hora", 160, ReviewViewModel.Row::when));
        table.getColumns().add(Ui.col("Entidad", 110, ReviewViewModel.Row::bank));
        table.getColumns().add(Ui.col("Monto", 110, ReviewViewModel.Row::amount));
        table.getColumns().add(Ui.col("Estado", 180, ReviewViewModel.Row::status));
        table.getColumns().add(Ui.col("Motivo", 300, ReviewViewModel.Row::reason));
        table.setPrefHeight(240);
        detail.setEditable(false);
        detail.setWrapText(true);
        VBox.setVgrow(detail, Priority.ALWAYS);
        table.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> select(b));
        validate.setOnAction(e -> doValidate());
        discard.setOnAction(e -> doDiscard());
        keep.setOnAction(e -> doKeep());
        select(null);
        root.getChildren().addAll(Ui.title("Pendientes de revisión"),
                Ui.muted("Estos pagos NO están confirmados. Compáralos con tu app o extracto bancario antes de validarlos."),
                table, Ui.heading("Detalle"), detail, new HBox(10, validate, discard, keep));
        root.setPadding(new Insets(18));
    }

    @Override
    public String id() {
        return "pending";
    }

    @Override
    public String title() {
        return "Pendientes";
    }

    @Override
    public Node node() {
        return root;
    }

    private Optional<ReviewViewModel.Row> selected() {
        return Optional.ofNullable(table.getSelectionModel().getSelectedItem());
    }

    private void select(ReviewViewModel.Row r) {
        detail.setText(r == null ? "Selecciona un evento para ver sus datos." : ReviewViewModel.detail(r.item()));
        boolean has = r != null;
        validate.setDisable(!has || !ReviewViewModel.canValidate(r.item()));
        discard.setDisable(!has);
        keep.setDisable(!has);
    }

    @Override
    public void refresh() {
        long keepId = selected().map(ReviewViewModel.Row::eventId).orElse(-1L);
        Background.run(() -> ctx.app.reviewService().pending(200).stream().map(ReviewViewModel::row).toList(), rows -> {
            table.getItems().setAll(rows);
            rows.stream().filter(r -> r.eventId() == keepId).findFirst()
                    .ifPresent(r -> table.getSelectionModel().select(r));
            if (selected().isEmpty()) {
                select(null);
            }
        }, e -> ctx.shell.banner("No se pudo cargar Pendientes: " + Ui.describe(e)));
    }

    private void doValidate() {
        selected().ifPresent(r -> {
            if (!Ui.confirm("Confirmar pago", "¿Confirmas que recibiste " + r.amount() + " en tu cuenta?\n"
                    + "Quedará registrado a nombre de " + ctx.actor() + ".")) {
                return;
            }
            run(() -> ctx.app.reviewService().validate(r.eventId(), ctx.actor(), null), "No se pudo validar");
        });
    }

    private void doDiscard() {
        selected().ifPresent(r -> Ui.ask("Descartar evento", "Motivo (obligatorio):", true).ifPresent(reason -> {
            String clean = ReviewViewModel.requireDiscardReason(reason);
            run(() -> ctx.app.reviewService().discard(r.eventId(), ctx.actor(), clean), "No se pudo descartar");
        }));
    }

    private void doKeep() {
        selected().ifPresent(r -> Ui.ask("Mantener pendiente", "Nota (opcional):", false).ifPresent(note ->
                run(() -> ctx.app.reviewService().keepPending(r.eventId(), ctx.actor(), note), "No se pudo registrar")));
    }

    private void run(Runnable work, String failTitle) {
        Background.run(work, this::refresh, e -> Ui.error(failTitle, Ui.describe(e)));
    }
}
