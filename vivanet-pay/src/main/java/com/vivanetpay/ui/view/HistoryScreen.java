package com.vivanetpay.ui.view;

import com.vivanetpay.domain.EventSource;
import com.vivanetpay.domain.EventStatus;
import com.vivanetpay.reporting.CsvExporter;
import com.vivanetpay.reporting.HistoryFilter;
import com.vivanetpay.reporting.HistoryRow;
import com.vivanetpay.ui.Background;
import com.vivanetpay.ui.Formats;
import com.vivanetpay.ui.Ui;
import com.vivanetpay.ui.UiContext;
import com.vivanetpay.ui.viewmodel.HistoryViewModel;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Historial (RF-014, RF-019, RF-020): tabla, filtros, detalle y exportación CSV. */
public final class HistoryScreen implements Screen {
    private record Choice<T>(String label, T value) {
        @Override
        public String toString() {
            return label;
        }
    }

    private final UiContext ctx;
    private final VBox root = new VBox(10);
    private final DatePicker from = new DatePicker();
    private final DatePicker to = new DatePicker();
    private final ComboBox<Choice<EventSource>> source = new ComboBox<>();
    private final ComboBox<Choice<String>> status = new ComboBox<>();
    private final TextField min = new TextField();
    private final TextField max = new TextField();
    private final TextField text = new TextField();
    private final Label summary = Ui.muted("");
    private final TableView<HistoryViewModel.Row> table = new TableView<>();
    private final TextArea detail = new TextArea();
    private HistoryFilter lastFilter = HistoryFilter.all();

    public HistoryScreen(UiContext ctx) {
        this.ctx = ctx;
        from.setPromptText("Desde");
        to.setPromptText("Hasta");
        min.setPromptText("Monto mínimo");
        max.setPromptText("Monto máximo");
        text.setPromptText("Remitente o referencia");
        List<Choice<EventSource>> sources = new ArrayList<>();
        sources.add(new Choice<>("Todas las fuentes", null));
        for (EventSource s : EventSource.values()) {
            sources.add(new Choice<>(Formats.source(s), s));
        }
        source.getItems().setAll(sources);
        source.getSelectionModel().selectFirst();
        List<Choice<String>> statuses = new ArrayList<>();
        statuses.add(new Choice<>("Todos los estados", null));
        statuses.add(new Choice<>("✔ Solo confirmados", "CONFIRMADOS"));
        statuses.add(new Choice<>("⚠ Solo por verificar", "POR_VERIFICAR"));
        for (EventStatus s : EventStatus.values()) {
            statuses.add(new Choice<>(Formats.status(s), s.name()));
        }
        status.getItems().setAll(statuses);
        status.getSelectionModel().selectFirst();

        Button search = new Button("Buscar");
        search.setDefaultButton(true);
        search.setOnAction(e -> refresh());
        Button clear = new Button("Limpiar filtros");
        clear.setOnAction(e -> {
            from.setValue(null);
            to.setValue(null);
            source.getSelectionModel().selectFirst();
            status.getSelectionModel().selectFirst();
            min.clear();
            max.clear();
            text.clear();
            refresh();
        });
        Button export = new Button("Exportar CSV…");
        export.setOnAction(e -> export());
        FlowPane filters = new FlowPane(8, 8, new Label("Fecha:"), from, to, source, status, min, max, text, search, clear, export);

        Ui.placeholder(table, "No hay movimientos con estos filtros");
        table.getColumns().add(Ui.col("Fecha y hora", 160, HistoryViewModel.Row::when));
        table.getColumns().add(Ui.col("Fuente", 120, HistoryViewModel.Row::source));
        table.getColumns().add(Ui.col("Entidad", 120, HistoryViewModel.Row::bank));
        table.getColumns().add(Ui.col("Monto", 110, HistoryViewModel.Row::amount));
        table.getColumns().add(Ui.col("Remitente", 140, HistoryViewModel.Row::sender));
        table.getColumns().add(Ui.col("Estado", 190, HistoryViewModel.Row::status));
        table.getSelectionModel().selectedItemProperty().addListener((o, a, b) ->
                detail.setText(b == null ? "" : HistoryViewModel.detail(b.raw())));
        VBox.setVgrow(table, Priority.ALWAYS);
        detail.setEditable(false);
        detail.setPrefRowCount(8);
        detail.setWrapText(true);
        root.getChildren().addAll(Ui.title("Historial"), filters, summary, table, Ui.heading("Detalle"), detail);
        root.setPadding(new Insets(18));
    }

    @Override
    public String id() {
        return "history";
    }

    @Override
    public String title() {
        return "Historial";
    }

    @Override
    public Node node() {
        return root;
    }

    private HistoryFilter currentFilter() {
        return HistoryViewModel.toFilter(new HistoryViewModel.FilterInput(from.getValue(), to.getValue(),
                source.getValue() == null ? null : source.getValue().value(),
                status.getValue() == null ? null : status.getValue().value(), min.getText(), max.getText(), text.getText()));
    }

    @Override
    public void refresh() {
        HistoryFilter f;
        try {
            f = currentFilter();
        } catch (IllegalArgumentException e) {
            Ui.error("Filtros no válidos", e.getMessage());
            return;
        }
        lastFilter = f;
        Background.run(() -> ctx.app.historyService().search(f).stream().map(HistoryViewModel::row).toList(),
                rows -> {
                    table.getItems().setAll(rows);
                    summary.setText(rows.size() + " resultado(s)"
                            + (rows.size() >= f.limit() ? " (se muestran los primeros " + f.limit() + "; afina los filtros)" : "")
                            + ". Horas en America/Bogota.");
                }, e -> ctx.shell.banner("No se pudo cargar el historial: " + Ui.describe(e)));
    }

    private void export() {
        HistoryFilter base = lastFilter;
        FileChooser fc = new FileChooser();
        fc.setTitle("Exportar historial filtrado");
        fc.setInitialFileName("historial-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".csv");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV (Excel)", "*.csv"));
        File file = fc.showSaveDialog(root.getScene().getWindow());
        if (file == null) {
            return;
        }
        HistoryFilter all = new HistoryFilter(base.fromDate(), base.toDate(), base.sources(), base.statuses(),
                base.minAmount(), base.maxAmount(), base.bank(), base.text(), 1_000_000, 0);
        Background.run(() -> {
            List<HistoryRow> rows = ctx.app.historyService().search(all);
            CsvExporter.write(rows, file.toPath());
            return rows.size();
        }, n -> Ui.info("Exportación lista", n + " fila(s) guardadas en\n" + file.getAbsolutePath()),
                e -> Ui.error("No se pudo exportar", Ui.describe(e)));
    }
}
