package com.vivanetpay.ui;

import com.vivanetpay.app.AppConfig;
import com.vivanetpay.app.AppContext;
import com.vivanetpay.app.Bootstrap;
import com.vivanetpay.domain.EventSource;
import com.vivanetpay.domain.RawEvent;
import com.vivanetpay.parsing.DefaultParsers;
import com.vivanetpay.security.SecretStores;
import com.vivanetpay.voice.SpeechEngines;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Herramienta manual (no es un test): captura las 7 pantallas en tema claro y oscuro. Uso: ver docs. */
public class ScreenshotTool extends Application {
    /** Punto de entrada sin heredar de Application (JavaFX desde el classpath). */
    public static final class Main {
        public static void main(String[] args) {
            Application.launch(ScreenshotTool.class, args);
        }
    }

    private static final List<String> IDS = List.of("dashboard", "history", "pending", "connectors", "voice", "settings", "diagnostics");

    @Override
    public void start(Stage stage) throws Exception {
        Path out = Path.of(System.getProperty("shots.dir"));
        Files.createDirectories(out);
        Path data = Files.createTempDirectory("vp-shots");
        System.setProperty(AppConfig.DATA_DIR_PROPERTY, data.toString());
        AppConfig config = AppConfig.load();
        Bootstrap.Result r = Bootstrap.start(AppContext.builder(config).speechEngine(SpeechEngines.create())
                .secretStore(SecretStores.forCurrentOs(config.dataDir())).parsers(DefaultParsers.all()));
        String[] texts = {"Ana Pérez te envió $125.000", "Luis Gómez te envió $48.500", "Te llegaron $2.300.000 de Carlos"};
        int i = 0;
        for (String t : texts) {
            r.context().ingestionService().ingest(new RawEvent("shot-" + i++, EventSource.ANDROID_NEQUI, "android-principal",
                    Instant.now(), Instant.now(), "Nequi", "com.nequi.MobileApp", "Nequi\n" + t, Map.of(), 1, null, false));
        }
        UiContext ctx = new UiContext(r);
        ctx.shell = new UiContext.Shell() {
            public void navigate(String id) { }
            public void banner(String m) { }
            public void requestExit() { }
            public void onPreferencesChanged() { }
        };
        MainWindow w = new MainWindow(ctx);
        Scene scene = new Scene(w.root(), 1100, 720);
        Theme.followSystem();
        Theme.apply(scene);
        stage.setScene(scene);
        stage.show();
        w.banner("Aviso de ejemplo: el conector Gmail requiere autorización.");
        List<String[]> jobs = new java.util.ArrayList<>();
        for (boolean dark : new boolean[]{false, true}) {
            for (String id : IDS) {
                jobs.add(new String[]{id, Boolean.toString(dark)});
            }
        }
        step(jobs, 0, w, scene, out);
    }

    private void step(List<String[]> jobs, int n, MainWindow w, Scene scene, Path out) {
        if (n >= jobs.size()) {
            Platform.exit();
            System.exit(0);
            return;
        }
        String[] j = jobs.get(n);
        Theme.setDark(Boolean.parseBoolean(j[1]));
        w.show(j[0]);
        PauseTransition p = new PauseTransition(Duration.millis(1200));
        p.setOnFinished(e -> {
            WritableImage img = scene.snapshot(null);
            BufferedImage bi = new BufferedImage((int) img.getWidth(), (int) img.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < bi.getHeight(); y++) {
                for (int x = 0; x < bi.getWidth(); x++) {
                    bi.setRGB(x, y, img.getPixelReader().getArgb(x, y));
                }
            }
            try {
                javax.imageio.ImageIO.write(bi, "png", new File(out.toFile(), (n < IDS.size() ? "claro-" : "oscuro-") + j[0] + ".png"));
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
            step(jobs, n + 1, w, scene, out);
        });
        p.play();
    }
}
