package com.vivanetpay.ui.view;

import com.vivanetpay.domain.AppTime;
import com.vivanetpay.ui.Background;
import com.vivanetpay.ui.Ui;
import com.vivanetpay.ui.UiContext;
import com.vivanetpay.ui.UiPreferences;
import com.vivanetpay.ui.WindowsStartup;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** Configuración (IDD §9.1). */
public final class SettingsScreen implements Screen {
    private final UiContext ctx;
    private final VBox root = new VBox(12);
    private final CheckBox startup = new CheckBox("Iniciar Vivanet Pay al iniciar sesión en Windows");
    private final CheckBox tray = new CheckBox("Al cerrar la ventana, seguir funcionando en la bandeja del sistema");
    private final Label startupNote = Ui.muted("");
    private boolean loading;

    public SettingsScreen(UiContext ctx) {
        this.ctx = ctx;
        startup.setOnAction(e -> {
            if (!loading) {
                changeStartup(startup.isSelected());
            }
        });
        tray.setOnAction(e -> {
            if (!loading) {
                boolean v = tray.isSelected();
                Background.run(() -> ctx.prefs.put(UiPreferences.MINIMIZE_TO_TRAY, v),
                        ctx.shell::onPreferencesChanged, err -> Ui.error("No se pudo guardar", Ui.describe(err)));
            }
        });
        root.getChildren().addAll(Ui.title("Configuración"), startup, startupNote, tray,
                Ui.heading("Zona horaria"), Ui.text(AppTime.BOGOTA.getId() + " (UTC-5, sin horario de verano) — solo lectura"),
                Ui.heading("Moneda"), Ui.text("COP — peso colombiano, sin decimales — solo lectura"));
        root.setPadding(new Insets(18));
    }

    @Override
    public String id() {
        return "settings";
    }

    @Override
    public String title() {
        return "Configuración";
    }

    @Override
    public Node node() {
        return root;
    }

    @Override
    public void refresh() {
        Background.run(() -> new boolean[]{ctx.prefs.getBoolean(UiPreferences.MINIMIZE_TO_TRAY, true),
                ctx.startup.isEnabled()}, v -> {
                    loading = true;
                    tray.setSelected(v[0]);
                    startup.setSelected(v[1]);
                    startup.setDisable(!ctx.startup.supported());
                    startupNote.setText(ctx.startup.supported() ? "" : "Disponible solo en Windows.");
                    loading = false;
                }, e -> ctx.shell.banner("No se pudo leer la configuración: " + Ui.describe(e)));
    }

    private void changeStartup(boolean enable) {
        Background.run(() -> {
            WindowsStartup.Result r = enable ? ctx.startup.enable(WindowsStartup.detectExecutable()) : ctx.startup.disable();
            if (r.ok()) {
                ctx.prefs.put(UiPreferences.START_WITH_WINDOWS, enable);
            }
            return r;
        }, r -> {
            if (!r.ok()) {
                loading = true;
                startup.setSelected(!enable);
                loading = false;
                Ui.error("Inicio con Windows", r.message());
            } else {
                startupNote.setText(r.message());
            }
        }, e -> Ui.error("Inicio con Windows", Ui.describe(e)));
    }
}
