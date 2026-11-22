package com.vivanetpay.ui;

import com.vivanetpay.app.AppConfig;
import com.vivanetpay.app.Bootstrap;
import com.vivanetpay.app.AppContext;
import com.vivanetpay.application.AppEvent;
import com.vivanetpay.application.AppEventBus;
import com.vivanetpay.domain.ConnectorStatus;
import com.vivanetpay.parsing.DefaultParsers;
import com.vivanetpay.security.SecretStores;
import com.vivanetpay.voice.SpeechEngines;
import com.vivanetpay.voice.VoiceSettings;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Aplicación JavaFX de Vivanet Pay. Inicia el núcleo ({@link Bootstrap}) en un hilo de fondo, mantiene la app viva
 * en la bandeja ({@code Platform.setImplicitExit(false)}) y refleja los eventos del {@link AppEventBus} con
 * {@code Platform.runLater}. Argumento {@code --minimized}: inicia sin mostrar la ventana (inicio con Windows).
 */
public class MainApp extends Application implements UiContext.Shell {
    private static final class Log {
        static final Logger L = LoggerFactory.getLogger(MainApp.class);
    }

    private Stage stage;
    private UiContext ctx;
    private MainWindow window;
    private TrayController tray;
    private boolean trayInstalled;
    private boolean minimizeToTray = true;
    private boolean trayHintShown;
    private final List<AppEventBus.Subscription> subscriptions = new ArrayList<>();
    private final AtomicBoolean refreshQueued = new AtomicBoolean();
    private final AtomicBoolean exiting = new AtomicBoolean();

    @Override
    public void start(Stage primary) {
        System.setProperty("java.awt.headless", "false");
        AppConfig config = AppConfig.load();
        if (System.getProperty(AppConfig.LOG_DIR_PROPERTY) == null) {
            System.setProperty(AppConfig.LOG_DIR_PROPERTY, config.logsDir().toString());
        }
        Platform.setImplicitExit(false);
        this.stage = primary;
        boolean minimized = getParameters().getRaw().contains(WindowsStartup.MINIMIZED_ARG);
        primary.setTitle("Vivanet Pay");
        primary.setMinWidth(900);
        primary.setMinHeight(620);
        Label loading = new Label("Iniciando Vivanet Pay…");
        loading.setStyle("-fx-font-size: 18px; -fx-padding: 40;");
        Scene scene = new Scene(new StackPane(loading), 1100, 720);
        applyCss(scene);
        primary.setScene(scene);
        primary.setOnCloseRequest(e -> {
            e.consume();
            onCloseRequested();
        });
        if (!minimized) {
            primary.show();
        }
        Thread boot = new Thread(() -> {
            try {
                Bootstrap.Result result = Bootstrap.start(AppContext.builder(config)
                        .speechEngine(SpeechEngines.create())
                        .secretStore(SecretStores.forCurrentOs(config.dataDir()))
                        .parsers(DefaultParsers.all()));
                Platform.runLater(() -> onReady(result, scene, minimized));
            } catch (Throwable t) {
                Log.L.error("No se pudo iniciar Vivanet Pay", t);
                Platform.runLater(() -> loading.setText("No se pudo iniciar Vivanet Pay:\n" + Ui.describe(t)
                        + "\n\nRevisa los logs en " + config.logsDir()));
                Platform.runLater(() -> stage.show());
            }
        }, "vivanet-bootstrap");
        boot.setDaemon(true);
        boot.start();
    }

    private static void applyCss(Scene scene) {
        var css = MainApp.class.getResource("/ui/vivanet.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
    }

    private void onReady(Bootstrap.Result result, Scene scene, boolean minimized) {
        ctx = new UiContext(result);
        ctx.shell = this;
        window = new MainWindow(ctx);
        scene.setRoot((javafx.scene.Parent) window.root());
        subscribeBus();
        tray = new TrayController(new TrayController.Actions() {
            @Override
            public void open() {
                Platform.runLater(MainApp.this::showWindow);
            }

            @Override
            public void toggleCapture() {
                Platform.runLater(MainApp.this::toggleCapture);
            }

            @Override
            public void toggleMute() {
                Platform.runLater(MainApp.this::toggleMute);
            }

            @Override
            public void exit() {
                Platform.runLater(() -> {
                    showWindow();
                    requestExit();
                });
            }
        });
        trayInstalled = tray.install();
        if (minimized && !trayInstalled) {
            stage.show(); // sin bandeja no se puede ocultar del todo
        }
        Background.run(() -> {
            minimizeToTray = ctx.prefs.getBoolean(UiPreferences.MINIMIZE_TO_TRAY, true);
            applySavedVoiceSettings();
            return Boolean.TRUE;
        }, ok -> updateTray(), e -> Log.L.warn("No se pudieron leer las preferencias: {}", e.getMessage()));
        updateTray();
        if (!result.databaseHealthy()) {
            banner("La base de datos tiene problemas de integridad. Ve a Diagnóstico y restaura un respaldo.");
        } else if (!result.license().status().allowsCapture()) {
            banner(result.license().message());
        }
        Log.L.info("Interfaz lista (bandeja: {}, minimizado: {})", trayInstalled, minimized);
    }

    private void applySavedVoiceSettings() {
        VoiceSettings cur = ctx.app.voiceService().settings();
        ctx.app.voiceService().updateSettings(new VoiceSettings(
                ctx.prefs.getBoolean(UiPreferences.VOICE_ENABLED, cur.enabled()),
                clamp(ctx.prefs.getInt(UiPreferences.VOICE_VOLUME, cur.volume()), 0, 100),
                clamp(ctx.prefs.getInt(UiPreferences.VOICE_RATE, cur.rate()), -10, 10),
                cur.voiceName(), cur.includeSender()));
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private void subscribeBus() {
        AppEventBus bus = ctx.app.eventBus();
        subscriptions.add(bus.subscribeAll(e -> Platform.runLater(() -> onEvent(e))));
    }

    /** Siempre en el hilo de JavaFX. */
    private void onEvent(AppEvent e) {
        if (exiting.get()) {
            return;
        }
        scheduleRefresh();
        updateTray();
        if (e instanceof AppEvent.AnnouncementNotPlayed n) {
            String msg = n.message() == null ? "Evento sin anunciar" : n.message();
            boolean silenced = ctx.app.voiceService().isMuted();
            notifyUser(silenced ? "Voz silenciada" : "Falló el aviso de voz", msg + (n.reason() == null ? "" : " (" + n.reason() + ")"), true);
        } else if (e instanceof AppEvent.ConnectorStatusChanged c && c.current().status().needsAttention()
                && c.current().status() != ConnectorStatus.PAUSADO) {
            notifyUser("Conector " + c.current().type() + ": " + c.current().status().label(),
                    Formats.recommendedAction(c.current().status()), true);
        } else if (e instanceof AppEvent.EventNeedsReview) {
            notifyUser("Pago por verificar", "Hay un evento pendiente de revisión.", false);
        }
    }

    private void scheduleRefresh() {
        if (refreshQueued.compareAndSet(false, true)) {
            Platform.runLater(() -> {
                refreshQueued.set(false);
                if (window != null) {
                    window.refreshCurrent();
                }
            });
        }
    }

    private void notifyUser(String title, String message, boolean error) {
        if (trayInstalled) {
            tray.notify(title, message, error);
        }
        if (!trayInstalled || stage.isShowing()) {
            banner(title + ": " + message);
        }
    }

    private void updateTray() {
        if (tray == null || ctx == null) {
            return;
        }
        boolean paused = ctx.capturePaused();
        boolean muted = ctx.app.voiceService().isMuted();
        List<String> lines = ctx.connectorStates().stream()
                .map(s -> s.type() + ": " + Formats.connectorStatus(s.status())).toList();
        tray.update(paused, muted, lines);
    }

    private void showWindow() {
        stage.show();
        stage.setIconified(false);
        stage.toFront();
        stage.requestFocus();
    }

    private void toggleCapture() {
        boolean pause = !ctx.capturePaused();
        Background.run(() -> ctx.setCapturePaused(pause), () -> {
            updateTray();
            scheduleRefresh();
        }, e -> banner("No se pudo cambiar la captura: " + Ui.describe(e)));
    }

    private void toggleMute() {
        ctx.app.voiceService().setMuted(!ctx.app.voiceService().isMuted());
        updateTray();
        scheduleRefresh();
    }

    private void onCloseRequested() {
        if (ctx == null) {
            doExit();
            return;
        }
        if (trayInstalled && minimizeToTray) {
            stage.hide();
            if (!trayHintShown) {
                trayHintShown = true;
                tray.notify("Vivanet Pay sigue funcionando", "Sigue detectando pagos desde la bandeja del sistema. "
                        + "Usa el icono para abrir o salir.", false);
            }
        } else {
            requestExit();
        }
    }

    // ---- UiContext.Shell ----

    @Override
    public void navigate(String screenId) {
        window.show(screenId);
    }

    @Override
    public void banner(String message) {
        if (window != null) {
            window.banner(message);
        }
    }

    @Override
    public void onPreferencesChanged() {
        Background.run(() -> {
            minimizeToTray = ctx.prefs.getBoolean(UiPreferences.MINIMIZE_TO_TRAY, true);
            return Boolean.TRUE;
        }, ok -> updateTray(), e -> { });
    }

    @Override
    public void requestExit() {
        if (Ui.confirm("Salir de Vivanet Pay", "Si sales, Vivanet Pay dejará de detectar y anunciar pagos.\n"
                + "¿Seguro que quieres salir?")) {
            doExit();
        }
    }

    private void doExit() {
        if (!exiting.compareAndSet(false, true)) {
            return;
        }
        subscriptions.forEach(AppEventBus.Subscription::close);
        if (tray != null) {
            tray.remove();
        }
        Thread closer = new Thread(() -> {
            try {
                if (ctx != null) {
                    ctx.app.close();
                }
            } catch (RuntimeException e) {
                Log.L.warn("Error al cerrar: {}", e.getMessage());
            } finally {
                Platform.exit();
                System.exit(0);
            }
        }, "vivanet-shutdown");
        closer.start();
    }

    @Override
    public void stop() {
        // Platform.exit() ya fue llamado por doExit; aquí no se cierra el contexto otra vez.
    }
}
