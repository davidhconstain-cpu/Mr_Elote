package com.vivanetpay.ui.view;

import com.vivanetpay.licensing.LicenseInfo;
import com.vivanetpay.ui.Background;
import com.vivanetpay.ui.DiagnosticsPackage;
import com.vivanetpay.ui.Formats;
import com.vivanetpay.ui.Ui;
import com.vivanetpay.ui.UiContext;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/** Diagnóstico (IDD §9.1, §10.3): versión, errores, logs, licencia, respaldo/restauración y paquete redactado. */
public final class DiagnosticsScreen implements Screen {
    private final UiContext ctx;
    private final ScrollPane root = new ScrollPane();
    private final Label version = Ui.text("");
    private final Label dataDir = Ui.text("");
    private final Label logsDir = Ui.text("");
    private final Label lastError = Ui.text("");
    private final Label license = Ui.text("");
    private final Label integrity = Ui.text("");
    private final Label sysInfo = Ui.muted("");

    public DiagnosticsScreen(UiContext ctx) {
        this.ctx = ctx;
        Button openLogs = new Button("Abrir carpeta de logs");
        openLogs.setOnAction(e -> openFolder(ctx.app.config().logsDir()));
        Button backup = new Button("Hacer respaldo ahora");
        backup.setOnAction(e -> backupNow());
        Button restore = new Button("Restaurar respaldo…");
        restore.setOnAction(e -> restore());
        Button export = new Button("Exportar paquete de diagnóstico…");
        export.setOnAction(e -> exportPackage());
        Button check = new Button("Verificar integridad");
        check.setOnAction(e -> refresh());
        VBox content = new VBox(10, Ui.title("Diagnóstico"),
                Ui.heading("Versión"), version, sysInfo,
                Ui.heading("Licencia / prueba"), license,
                Ui.heading("Base de datos"), integrity, dataDir,
                Ui.heading("Último error registrado"), lastError,
                Ui.heading("Logs (datos sensibles redactados)"), logsDir,
                new HBox(8, openLogs, check),
                Ui.heading("Respaldo"), new HBox(8, backup, restore),
                Ui.heading("Soporte"), export);
        content.setPadding(new Insets(18));
        root.setContent(content);
        root.setFitToWidth(true);
    }

    @Override
    public String id() {
        return "diagnostics";
    }

    @Override
    public String title() {
        return "Diagnóstico";
    }

    @Override
    public Node node() {
        return root;
    }

    public static String appVersion() {
        String v = DiagnosticsScreen.class.getPackage().getImplementationVersion();
        return v == null ? "0.1.0-SNAPSHOT (desarrollo)" : v;
    }

    private String summary() {
        return "Vivanet Pay " + appVersion() + "\nJava " + System.getProperty("java.version") + "\nSO "
                + System.getProperty("os.name") + " " + System.getProperty("os.version") + "\nMotor de voz: "
                + ctx.app.speechEngine().id() + "\nConectores: " + ctx.connectorStates();
    }

    /** Lectura de diagnóstico calculada fuera del hilo de UI. */
    private record Snapshot(LicenseInfo license, List<String> problems, Optional<String> lastError) {
    }

    @Override
    public void refresh() {
        version.setText("Vivanet Pay " + appVersion());
        sysInfo.setText("Java " + System.getProperty("java.version") + " · " + System.getProperty("os.name"));
        dataDir.setText("Datos: " + ctx.app.config().dataDir());
        logsDir.setText(ctx.app.config().logsDir().toString());
        Background.run(() -> new Snapshot(ctx.app.licenseService().check(), ctx.app.maintenance().integrityCheck(),
                DiagnosticsPackage.lastError(ctx.app.config().logsDir())), r -> {
            LicenseInfo li = r.license();
            license.setText(li.status().label() + ". " + li.message() + (li.trialEndsAt() == null ? ""
                    : " (fin de prueba: " + Formats.time(li.trialEndsAt()) + ")"));
            integrity.setText(r.problems().isEmpty() ? "✔ La base de datos está íntegra."
                    : "✖ Problemas de integridad: " + r.problems() + ". Restaura un respaldo.");
            lastError.setText(r.lastError().orElse("Ninguno."));
        }, e -> lastError.setText("No se pudo leer el diagnóstico: " + Ui.describe(e)));
    }

    private void backupNow() {
        Background.run(() -> ctx.app.maintenance().backupToDirectory(ctx.app.config().backupsDir()),
                p -> Ui.info("Respaldo creado", p.toString()), e -> Ui.error("No se pudo respaldar", Ui.describe(e)));
    }

    private void restore() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Elegir respaldo a restaurar");
        File dir = ctx.app.config().backupsDir().toFile();
        if (dir.isDirectory()) {
            fc.setInitialDirectory(dir);
        }
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Respaldos (*.db)", "*.db"));
        File f = fc.showOpenDialog(root.getScene().getWindow());
        if (f == null) {
            return;
        }
        if (!Ui.confirm("Restaurar respaldo", "Se REEMPLAZARÁN los datos actuales por los del respaldo:\n" + f.getName()
                + "\n\nLos movimientos posteriores al respaldo se perderán de esta base. Se detendrá la captura y "
                + "Vivanet Pay se cerrará para reiniciarse. ¿Continuar?")) {
            return;
        }
        Background.run(() -> {
            ctx.app.connectorRegistry().stopAll();
            ctx.app.maintenance().restoreFrom(f.toPath());
        }, () -> {
            Ui.info("Respaldo restaurado", "Vuelve a abrir Vivanet Pay para continuar.");
            ctx.shell.requestExit();
        }, e -> Ui.error("No se pudo restaurar", Ui.describe(e) + "\nReinicia Vivanet Pay para reanudar la captura."));
    }

    private void exportPackage() {
        if (!Ui.confirm("Paquete de diagnóstico", "Se creará un archivo ZIP con la versión, el estado de los "
                + "conectores y los logs recientes, con tokens, claves y correos enmascarados. No incluye la base de "
                + "datos. Compártelo solo con soporte. ¿Crear el paquete?")) {
            return;
        }
        FileChooser fc = new FileChooser();
        fc.setTitle("Guardar paquete de diagnóstico");
        fc.setInitialFileName("vivanetpay-diagnostico-"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".zip");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("ZIP", "*.zip"));
        File f = fc.showSaveDialog(root.getScene().getWindow());
        if (f == null) {
            return;
        }
        String summary = summary();
        Path logs = ctx.app.config().logsDir();
        Background.run(() -> DiagnosticsPackage.create(logs, summary, f.toPath()),
                p -> Ui.info("Paquete creado", p.toString()), e -> Ui.error("No se pudo crear el paquete", Ui.describe(e)));
    }

    /** Abre la carpeta con el explorador del sistema (llamada AWT bloqueante: fuera del hilo de JavaFX). */
    private void openFolder(Path dir) {
        Background.run(() -> {
            if (!java.awt.Desktop.isDesktopSupported() || !java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.OPEN)) {
                return false;
            }
            java.awt.Desktop.getDesktop().open(dir.toFile());
            return true;
        }, opened -> {
            if (!opened) {
                Ui.info("Carpeta de logs", dir.toString());
            }
        }, e -> Ui.info("Carpeta de logs", dir.toString()));
    }
}
