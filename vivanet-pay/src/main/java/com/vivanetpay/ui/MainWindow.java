package com.vivanetpay.ui;

import com.vivanetpay.ui.view.ConnectorsScreen;
import com.vivanetpay.ui.view.DashboardScreen;
import com.vivanetpay.ui.view.DiagnosticsScreen;
import com.vivanetpay.ui.view.HistoryScreen;
import com.vivanetpay.ui.view.PendingScreen;
import com.vivanetpay.ui.view.Screen;
import com.vivanetpay.ui.view.SettingsScreen;
import com.vivanetpay.ui.view.VoiceScreen;
import javafx.scene.AccessibleRole;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
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
    private final HBox bannerBar = new HBox(10);
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
        int n = 0;
        for (Screen s : screens.values()) {
            n++;
            ToggleButton b = new ToggleButton(s.title());
            b.getStyleClass().add("nav-button");
            b.setMaxWidth(Double.MAX_VALUE);
            b.setToggleGroup(group);
            b.setOnAction(e -> show(s.id()));
            b.setAccessibleText("Pantalla " + s.title());
            b.setAccessibleHelp("Atajo Ctrl+" + n);
            b.setTooltip(new Tooltip(s.title() + " (Ctrl+" + n + ")"));
            buttons.put(s.id(), b);
            nav.getChildren().add(b);
        }
        nav.setAccessibleText("Navegación principal");
        // evita des-seleccionar al pulsar de nuevo el botón activo
        group.selectedToggleProperty().addListener((o, old, now) -> {
            if (now == null && old != null) {
                group.selectToggle(old);
            }
        });
        banner.getStyleClass().add("banner");
        banner.setWrapText(true);
        banner.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(banner, Priority.ALWAYS);
        // Cerrable con ratón o teclado (antes solo con clic sobre el texto)
        Button close = new Button("Cerrar aviso");
        close.getStyleClass().add("banner-close");
        close.setOnAction(e -> hideBanner());
        bannerBar.getStyleClass().add("banner-bar");
        bannerBar.getChildren().addAll(banner, close);
        bannerBar.setAccessibleRole(AccessibleRole.TEXT);
        bannerBar.setVisible(false);
        bannerBar.setManaged(false);
        root.setLeft(nav);
        root.setCenter(center);
        root.setTop(bannerBar);
        root.sceneProperty().addListener((o, old, scene) -> {
            if (scene != null) {
                installShortcuts(scene);
            }
        });
        show("dashboard");
    }

    /** Ctrl+1..7 abren cada pantalla en el orden de la navegación. */
    private void installShortcuts(Scene scene) {
        int n = 0;
        for (String id : screens.keySet()) {
            n++;
            KeyCode code = KeyCode.getKeyCode(Integer.toString(n));
            if (code != null) {
                scene.getAccelerators().put(new KeyCodeCombination(code, KeyCombination.SHORTCUT_DOWN), () -> show(id));
            }
        }
    }

    public Parent root() {
        return root;
    }

    public void show(String id) {
        Screen s = screens.get(id);
        if (s == null) {
            return;
        }
        currentId = id;
        center.getChildren().setAll(s.node());
        ToggleButton selected = buttons.get(id);
        selected.setSelected(true);
        // Si el foco quedó en otro botón del menú (p. ej. al navegar por atajo o desde un botón), lo movemos al
        // activo: el contorno de foco no debe señalar una pantalla que no es la actual.
        Scene scene = root.getScene();
        if (scene != null && buttons.containsValue(scene.getFocusOwner()) && scene.getFocusOwner() != selected) {
            selected.requestFocus();
        }
        s.refresh();
    }

    /** Refresca la pantalla visible (llamado por eventos del bus). */
    public void refreshCurrent() {
        if (currentId != null) {
            screens.get(currentId).refresh();
        }
    }

    public void banner(String message) {
        banner.setText("⚠ " + message);
        bannerBar.setAccessibleText("Aviso: " + message);
        bannerBar.setVisible(true);
        bannerBar.setManaged(true);
    }

    private void hideBanner() {
        bannerBar.setVisible(false);
        bannerBar.setManaged(false);
    }
}
