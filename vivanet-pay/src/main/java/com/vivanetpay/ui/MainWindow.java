package com.vivanetpay.ui;

import com.vivanetpay.ui.view.ConnectorsScreen;
import com.vivanetpay.ui.view.DashboardScreen;
import com.vivanetpay.ui.view.DiagnosticsScreen;
import com.vivanetpay.ui.view.HistoryScreen;
import com.vivanetpay.ui.view.PendingScreen;
import com.vivanetpay.ui.view.Screen;
import com.vivanetpay.ui.view.SettingsScreen;
import com.vivanetpay.ui.view.VoiceScreen;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.Map;

/** Contenedor con navegación lateral y barra de avisos. */
public final class MainWindow {
    private final BorderPane root = new BorderPane();
    private final StackPane center = new StackPane();
    private final Map<String, Screen> screens = new LinkedHashMap<>();
    private final Map<String, ToggleButton> buttons = new LinkedHashMap<>();
    private final ToggleGroup group = new ToggleGroup();
    private final Label banner = new Label();
    private String currentId;

    public MainWindow(UiContext ctx) {
        for (Screen s : new Screen[]{new DashboardScreen(ctx), new HistoryScreen(ctx), new PendingScreen(ctx),
                new ConnectorsScreen(ctx), new VoiceScreen(ctx), new SettingsScreen(ctx), new DiagnosticsScreen(ctx)}) {
            screens.put(s.id(), s);
        }
        VBox nav = new VBox(2);
        nav.getStyleClass().add("nav");
        nav.setPrefWidth(190);
        Label brand = new Label("Vivanet Pay");
        brand.getStyleClass().add("nav-title");
        nav.getChildren().add(brand);
        for (Screen s : screens.values()) {
            ToggleButton b = new ToggleButton(s.title());
            b.getStyleClass().add("nav-button");
            b.setMaxWidth(Double.MAX_VALUE);
            b.setToggleGroup(group);
            b.setOnAction(e -> show(s.id()));
            buttons.put(s.id(), b);
            nav.getChildren().add(b);
        }
        // evita des-seleccionar al pulsar de nuevo el botón activo
        group.selectedToggleProperty().addListener((o, old, now) -> {
            if (now == null && old != null) {
                group.selectToggle(old);
            }
        });
        banner.getStyleClass().add("banner");
        banner.setWrapText(true);
        banner.setMaxWidth(Double.MAX_VALUE);
        banner.setVisible(false);
        banner.setManaged(false);
        banner.setOnMouseClicked(e -> hideBanner());
        root.setLeft(nav);
        root.setCenter(center);
        root.setTop(banner);
        show("dashboard");
    }

    public Node root() {
        return root;
    }

    public void show(String id) {
        Screen s = screens.get(id);
        if (s == null) {
            return;
        }
        currentId = id;
        center.getChildren().setAll(s.node());
        buttons.get(id).setSelected(true);
        s.refresh();
    }

    /** Refresca la pantalla visible (llamado por eventos del bus). */
    public void refreshCurrent() {
        if (currentId != null) {
            screens.get(currentId).refresh();
        }
    }

    public void banner(String message) {
        banner.setText("⚠ " + message + "   (clic para cerrar)");
        banner.setVisible(true);
        banner.setManaged(true);
    }

    private void hideBanner() {
        banner.setVisible(false);
        banner.setManaged(false);
    }
}
