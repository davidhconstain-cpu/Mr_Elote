package com.vivanetpay.ui.view;

import com.vivanetpay.ui.Background;
import com.vivanetpay.ui.Ui;
import com.vivanetpay.ui.UiContext;
import com.vivanetpay.ui.UiPreferences;
import com.vivanetpay.voice.VoiceSettings;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.VBox;

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
            refreshMuteText();
            ctx.shell.onPreferencesChanged();
        });
        volume.valueProperty().addListener((o, a, b) -> volumeText.setText("Volumen: " + b.intValue() + " %"));
        rate.valueProperty().addListener((o, a, b) -> rateText.setText("Velocidad: " + b.intValue() + " (0 = normal)"));
        volume.valueChangingProperty().addListener((o, a, changing) -> {
            if (!changing) {
                save();
            }
        });
        rate.valueChangingProperty().addListener((o, a, changing) -> {
            if (!changing) {
                save();
            }
        });
        volume.setOnMouseReleased(e -> save());
        rate.setOnMouseReleased(e -> save());
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
        refreshMuteText();
        engine.setText("Motor de voz: " + ctx.app.speechEngine().id()
                + (ctx.app.speechEngine().isAvailable() ? " (disponible)" : " (NO disponible: revisa el audio de Windows)"));
    }

    private void refreshMuteText() {
        mute.setText(mute.isSelected() ? "🔇 Voz silenciada: pulsa para activar" : "Silenciar ahora");
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
