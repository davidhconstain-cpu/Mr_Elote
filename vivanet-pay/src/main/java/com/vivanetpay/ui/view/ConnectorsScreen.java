package com.vivanetpay.ui.view;

import com.vivanetpay.integration.AuthorizableConnector;
import com.vivanetpay.integration.Connector;
import com.vivanetpay.integration.android.AndroidWebhookConnector;
import com.vivanetpay.security.SecretBytes;
import com.vivanetpay.ui.Background;
import com.vivanetpay.ui.Formats;
import com.vivanetpay.ui.Ui;
import com.vivanetpay.ui.UiContext;
import com.vivanetpay.ui.viewmodel.ConnectorsViewModel;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.Comparator;
import java.util.List;

/** Conectores (RF-004, RF-017, RF-025): estado, acción recomendada, pausar/reanudar, autorizar, probar. */
public final class ConnectorsScreen implements Screen {
    private final UiContext ctx;
    private final ScrollPane root = new ScrollPane();
    private final VBox list = new VBox(12);

    public ConnectorsScreen(UiContext ctx) {
        this.ctx = ctx;
        VBox content = new VBox(12, Ui.title("Conectores"), list);
        content.setPadding(new Insets(18));
        root.setContent(content);
        root.setFitToWidth(true);
    }

    @Override
    public String id() {
        return "connectors";
    }

    @Override
    public String title() {
        return "Conectores";
    }

    @Override
    public Node node() {
        return root;
    }

    /** Instantánea calculada fuera del hilo de UI (isAuthorized puede tocar el almacén de secretos). */
    private record Snapshot(Connector connector, ConnectorsViewModel vm, String account,
                            ConnectorsViewModel.AndroidInfo android) {
    }

    @Override
    public void refresh() {
        List<Connector> all = ctx.app.connectorRegistry().all().stream().sorted(Comparator.comparing(Connector::id)).toList();
        Background.run(() -> all.stream().map(c -> new Snapshot(c, ConnectorsViewModel.of(c),
                c instanceof AuthorizableConnector ac && ac.isAuthorized() ? ac.authorizedAccount().orElse(null) : null,
                c instanceof AndroidWebhookConnector a ? ConnectorsViewModel.AndroidInfo.of(a) : null)).toList(),
                this::render, e -> Ui.error("No se pudo leer el estado de los conectores", Ui.describe(e)));
    }

    private void render(List<Snapshot> all) {
        list.getChildren().clear();
        if (all.isEmpty()) {
            list.getChildren().add(Ui.text("No hay conectores activos. Por defecto arrancan Gmail y Android; "
                    + "alguien los desactivó con connectors.enabled=none en config.properties dentro de " + ctx.app.config().dataDir() + "."));
        }
        if (!ctx.boot.missingProviders().isEmpty()) {
            list.getChildren().add(Ui.text("⚠ Habilitados pero sin implementación disponible: " + ctx.boot.missingProviders()));
        }
        for (Snapshot snap : all) {
            list.getChildren().add(card(snap));
        }
    }

    private Node card(Snapshot snap) {
        Connector c = snap.connector();
        ConnectorsViewModel vm = snap.vm();
        Label name = new Label(vm.name() + "  ·  " + vm.status());
        name.getStyleClass().add("section-heading");
        VBox box = Ui.card("card-neutral", name, Ui.text("Qué hacer: " + vm.action()));
        if (!vm.detail().isBlank()) {
            box.getChildren().add(Ui.muted(vm.detail()));
        }
        if (snap.account() != null) {
            box.getChildren().add(Ui.muted("Cuenta autorizada: " + snap.account()));
        }
        ConnectorsViewModel.AndroidInfo info = snap.android();
        if (info != null) {
            box.getChildren().add(Ui.text("Dirección del receptor: "
                    + (info.endpoints().isEmpty() ? "no está escuchando" : String.join("  |  ", info.endpoints()))));
            box.getChildren().add(Ui.muted("Autenticación: " + (info.authMode().equals("token")
                    ? "token (Bearer, para MacroDroid)" : "firma HMAC") + " · HTTPS: " + (info.tls() ? "sí" : "no")));
            for (String w : info.warnings()) {
                box.getChildren().add(Ui.text("⚠ " + w));
            }
            box.getChildren().add(Ui.muted(info.statsText()));
            if (info.previousSecretValidUntil() != null) {
                box.getChildren().add(Ui.muted("El secreto anterior sigue válido hasta "
                        + Formats.time(info.previousSecretValidUntil()) + "."));
            }
        }
        box.getChildren().add(Ui.muted("Última sincronización: " + vm.lastSuccess()));
        HBox buttons = new HBox(8);
        Button pause = new Button(vm.paused() ? "Reanudar" : "Pausar");
        pause.setOnAction(e -> act(() -> {
            if (vm.paused()) {
                c.resume();
            } else {
                c.pause();
            }
        }, "No se pudo cambiar el estado"));
        buttons.getChildren().add(pause);
        if (vm.canAuthorize()) {
            AuthorizableConnector a = (AuthorizableConnector) c;
            Button auth = new Button(vm.authorized() ? "Volver a autorizar" : "Autorizar");
            auth.setTooltip(new javafx.scene.control.Tooltip("Abre el navegador para que inicies sesión en Google."));
            auth.setOnAction(e -> {
                auth.setDisable(true);
                Ui.info("Autorización de " + vm.name(), "Se abrirá el navegador. Inicia sesión y concede el permiso de "
                        + "solo lectura. Esta ventana se actualizará cuando termine.");
                Background.run(() -> {
                    a.authorize();
                    return Boolean.TRUE;
                }, ok -> {
                    Ui.info("Autorización", vm.name() + " quedó autorizado.");
                    refresh();
                    ctx.shell.onPreferencesChanged();
                }, err -> {
                    auth.setDisable(false);
                    Ui.error("No se pudo autorizar " + vm.name(), Ui.describe(err));
                    refresh();
                });
            });
            Button revoke = new Button("Revocar acceso");
            revoke.setDisable(!vm.authorized());
            revoke.setOnAction(e -> {
                if (Ui.confirm("Revocar acceso", "Se eliminará la autorización de " + vm.name()
                        + " y dejará de capturar pagos. ¿Continuar?")) {
                    act(a::revoke, "No se pudo revocar");
                }
            });
            buttons.getChildren().addAll(auth, revoke);
        }
        if (vm.canTestReception()) {
            Button test = new Button("Probar recepción");
            test.setOnAction(e -> {
                test.setDisable(true);
                Background.run(() -> ConnectorsViewModel.runTest(c), msg -> {
                    Ui.info("Prueba de recepción", msg);
                    refresh();
                }, err -> {
                    test.setDisable(false);
                    Ui.error("La prueba falló", Ui.describe(err));
                });
            });
            buttons.getChildren().add(test);
        }
        if (c instanceof AndroidWebhookConnector android) {
            Button show = new Button("Mostrar secreto");
            show.setOnAction(e -> {
                if (Ui.confirm("Mostrar secreto", "El secreto es como una contraseña: quien lo tenga puede enviar avisos "
                        + "falsos a Vivanet Pay. Muéstralo solo si nadie más ve la pantalla. ¿Mostrarlo?")) {
                    Background.run(android::revealSecret,
                            secret -> showSecret("Secreto del receptor Android", null, secret),
                            err -> Ui.error("No se pudo leer el secreto", Ui.describe(err)));
                }
            });
            Button rotate = new Button("Rotar secreto");
            rotate.setOnAction(e -> {
                if (Ui.confirm("Rotar secreto", "Se generará un secreto nuevo. El anterior seguirá valiendo unos minutos "
                        + "para que actualices MacroDroid en el celular. ¿Rotar ahora?")) {
                    Background.run(android::rotateSecret, secret -> {
                        showSecret("Secreto nuevo", "Cópialo en la cabecera Authorization del celular: Bearer <secreto>. "
                                + "El anterior vale solo unos minutos.", secret);
                        refresh();
                    }, err -> Ui.error("No se pudo rotar el secreto", Ui.describe(err)));
                }
            });
            Button revokeSecret = new Button("Revocar secreto");
            revokeSecret.setOnAction(e -> {
                if (Ui.confirm("Revocar secreto", "El secreto actual dejará de funcionar DE INMEDIATO y se generará uno "
                        + "nuevo. El celular no podrá enviar avisos hasta que lo actualices. ¿Continuar?")) {
                    Background.run(android::revokeSecret, secret -> {
                        showSecret("Secreto nuevo", "El anterior ya no funciona.", secret);
                        refresh();
                    }, err -> Ui.error("No se pudo revocar el secreto", Ui.describe(err)));
                }
            });
            buttons.getChildren().addAll(show, rotate, revokeSecret);
        }
        box.getChildren().add(buttons);
        return box;
    }

    /** Muestra el secreto oculto por defecto; al cerrar se borra de memoria y de los controles. Nunca se registra. */
    private void showSecret(String title, String note, char[] secret) {
        PasswordField hidden = new PasswordField();
        hidden.setText(new String(secret));
        hidden.setEditable(false);
        TextField visible = new TextField();
        visible.setEditable(false);
        visible.setManaged(false);
        visible.setVisible(false);
        CheckBox reveal = new CheckBox("Mostrar en pantalla");
        reveal.selectedProperty().addListener((o, was, now) -> {
            if (now) {
                visible.setText(hidden.getText());
            } else {
                visible.clear();
            }
            visible.setVisible(now);
            visible.setManaged(now);
            hidden.setVisible(!now);
            hidden.setManaged(!now);
        });
        Button copy = new Button("Copiar");
        copy.setOnAction(e -> {
            javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
            cc.putString(hidden.getText());
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(cc);
        });
        VBox content = new VBox(10);
        if (note != null) {
            content.getChildren().add(Ui.text(note));
        }
        content.getChildren().addAll(hidden, visible, new HBox(10, reveal, copy),
                Ui.muted("No lo compartas ni lo envíes por mensajes. Vivanet Pay nunca lo escribe en los registros."));
        Dialog<Void> d = new Dialog<>();
        d.setTitle(title);
        d.setHeaderText(title);
        d.getDialogPane().setContent(content);
        d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        try {
            d.showAndWait();
        } finally {
            SecretBytes.wipe(secret);
            hidden.clear();
            visible.clear();
        }
    }

    private interface Throwing {
        void run() throws Exception;
    }

    private void act(Throwing work, String failTitle) {
        Background.run(() -> {
            work.run();
            return Boolean.TRUE;
        }, ok -> {
            refresh();
            ctx.shell.onPreferencesChanged();
        }, e -> Ui.error(failTitle, Ui.describe(e)));
    }
}
