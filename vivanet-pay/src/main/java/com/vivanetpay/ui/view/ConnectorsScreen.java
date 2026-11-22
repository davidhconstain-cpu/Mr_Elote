package com.vivanetpay.ui.view;

import com.vivanetpay.integration.AuthorizableConnector;
import com.vivanetpay.integration.Connector;
import com.vivanetpay.ui.Background;
import com.vivanetpay.ui.Ui;
import com.vivanetpay.ui.UiContext;
import com.vivanetpay.ui.viewmodel.ConnectorsViewModel;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
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

    @Override
    public void refresh() {
        List<Connector> all = ctx.app.connectorRegistry().all().stream().sorted(Comparator.comparing(Connector::id)).toList();
        list.getChildren().clear();
        if (all.isEmpty()) {
            list.getChildren().add(Ui.text("No hay conectores activos. Se habilitan en config.properties "
                    + "(connectors.enabled=gmail,android) dentro de " + ctx.app.config().dataDir() + "."));
        }
        if (!ctx.boot.missingProviders().isEmpty()) {
            list.getChildren().add(Ui.text("⚠ Habilitados pero sin implementación disponible: " + ctx.boot.missingProviders()));
        }
        for (Connector c : all) {
            list.getChildren().add(card(c));
        }
    }

    private Node card(Connector c) {
        ConnectorsViewModel vm = ConnectorsViewModel.of(c);
        Label name = new Label(vm.name() + "  ·  " + vm.status());
        name.getStyleClass().add("section-heading");
        VBox box = Ui.card("card-neutral", name, Ui.text("Qué hacer: " + vm.action()));
        if (!vm.detail().isBlank()) {
            box.getChildren().add(Ui.muted(vm.detail()));
        }
        if (c instanceof AuthorizableConnector ac && ac.isAuthorized()) {
            ac.authorizedAccount().ifPresent(acc -> box.getChildren().add(Ui.muted("Cuenta autorizada: " + acc)));
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
            auth.setOnAction(e -> act(a::authorize, "No se pudo autorizar"));
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
            test.setOnAction(e -> Background.run(() -> ConnectorsViewModel.runTest(c),
                    msg -> {
                        Ui.info("Prueba de recepción", msg);
                        refresh();
                    }, err -> Ui.error("La prueba falló", Ui.describe(err))));
            buttons.getChildren().add(test);
        }
        box.getChildren().add(buttons);
        return box;
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
