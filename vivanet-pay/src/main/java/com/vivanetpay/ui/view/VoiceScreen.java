package com.vivanetpay.ui.view;

import com.vivanetpay.ui.Background;
import com.vivanetpay.ui.Ui;
import com.vivanetpay.ui.UiContext;
import com.vivanetpay.ui.UiPreferences;
import com.vivanetpay.voice.VoiceSettings;
import javafx.animation.PauseTransition;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/** Voz (RF-015, RF-016): activar, silenciar al instante, volumen, velocidad y prueba. */
public final class VoiceScreen implements Screen {
    private final UiContext ctx;
    private final VBox root = new VBox(12);
    private final CheckBox enabled = new CheckBox("Anunciar pagos por voz");
    private final ToggleButton mute = new ToggleButton("Silenciar ahora");
    private final Slider volume = new Slider(0, 100, 80);
    private final Slider rate = new Slider(-10, 10, 0);
    private final Label volumeText = new Label();
    private final Label rateText = new Label();
    private final Label engine = Ui.muted("");
    private boolean loading;

    public VoiceScreen(UiContext ctx) {
        this.ctx = ctx;
        volume.setShowTickLabels(true);
        volume.setMajorTickUnit(25);
        rate.setShowTickLabels(true);
        rate.setMajorTickUnit(5);
        rate.setSnapToTicks(true);
        rate.setMinorTickCount(0);
        Button test = new Button("Probar voz");
        test.setOnAction(e -> ctx.app.voiceService().testVoice());
        enabled.setOnAction(e -> save());
        mute.setOnAction(e -> {
            ctx.app.voiceService().setMuted(mute.isSelected());
            ctx.shell.onPreferencesChanged();
        });
        // Textos derivados por binding (antes se actualizaban a mano con listeners)
        volumeText.textProperty().bind(Bindings.createStringBinding(
                () -> "Volumen: " + (int) volume.getValue() + " %", volume.valueProperty()));
        rateText.textProperty().bind(Bindings.createStringBinding(
                () -> "Velocidad: " + (int) rate.getValue() + " (0 = normal)", rate.valueProperty()));
        mute.textProperty().bind(Bindings.when(mute.selectedProperty())
                .then("🔇 Voz silenciada: pulsa para activar").otherwise("Silenciar ahora"));
        volume.setAccessibleText("Volumen de la voz, de 0 a 100 por ciento");
        rate.setAccessibleText("Velocidad de la voz, de menos 10 a 10; 0 es normal");
        // Guardado con pausa (debounce): también guarda al mover el control con el teclado, no solo con el ratón.
        PauseTransition debounce = new PauseTransition(Duration.millis(400));
        debounce.setOnFinished(e -> save());
        volume.valueProperty().addListener((o, a, b) -> {
            if (!loading) {
                debounce.playFromStart();
            }
        });
        rate.valueProperty().addListener((o, a, b) -> {
            if (!loading) {
                debounce.playFromStart();
            }
        });
        root.getChildren().addAll(Ui.title("Voz"), enabled, mute, volumeText, volume, rateText, rate, test, engine,
                Ui.muted("El anuncio no incluye el nombre del remitente y no se reproducen movimientos históricos. "
                        + "Si la voz está silenciada o falla, verás un aviso visual."));
        root.setPadding(new Insets(18));
    }

    @Override
    public String id() {
        return "voice";
    }

    @Override
    public String title() {
        return "Voz";
    }

    @Override
    public Node node() {
        return root;
    }

    @Override
    public void refresh() {
        VoiceSettings s = ctx.app.voiceService().settings();
        loading = true;
        enabled.setSelected(s.enabled());
        volume.setValue(s.volume());
        rate.setValue(s.rate());
        mute.setSelected(ctx.app.voiceService().isMuted());
        loading = false;
        engine.setText("Motor de voz: " + ctx.app.speechEngine().id()
                + (ctx.app.speechEngine().isAvailable() ? " (disponible)" : " (NO disponible: revisa el audio de Windows)"));
    }

    private void save() {
        if (loading) {
            return;
        }
        VoiceSettings old = ctx.app.voiceService().settings();
        VoiceSettings next = new VoiceSettings(enabled.isSelected(), (int) Math.round(volume.getValue()),
                (int) Math.round(rate.getValue()), old.voiceName(), old.includeSender());
        ctx.app.voiceService().updateSettings(next);
        Background.run(() -> {
            ctx.prefs.put(UiPreferences.VOICE_ENABLED, next.enabled());
            ctx.prefs.put(UiPreferences.VOICE_VOLUME, next.volume());
            ctx.prefs.put(UiPreferences.VOICE_RATE, next.rate());
        }, () -> { }, e -> ctx.shell.banner("No se pudieron guardar las preferencias de voz: " + Ui.describe(e)));
    }
}
