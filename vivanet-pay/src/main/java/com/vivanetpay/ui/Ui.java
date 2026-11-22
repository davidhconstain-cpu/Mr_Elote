package com.vivanetpay.ui;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.VBox;

import java.util.Optional;
import java.util.function.Function;

/** Utilidades de construcción de controles. */
public final class Ui {
    private Ui() {
    }

    public static Label title(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("screen-title");
        return l;
    }

    public static Label heading(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("section-heading");
        return l;
    }

    public static Label text(String text) {
        Label l = new Label(text);
        l.setWrapText(true);
        return l;
    }

    public static Label muted(String text) {
        Label l = text(text);
        l.getStyleClass().add("muted");
        return l;
    }

    public static VBox card(String styleClass, javafx.scene.Node... children) {
        VBox box = new VBox(6, children);
        box.getStyleClass().addAll("card", styleClass);
        box.setPadding(new Insets(14));
        return box;
    }

    public static <T> TableColumn<T, String> col(String title, double width, Function<T, String> f) {
        TableColumn<T, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cd -> new ReadOnlyStringWrapper(f.apply(cd.getValue())));
        c.setPrefWidth(width);
        return c;
    }

    public static <T> void placeholder(TableView<T> t, String text) {
        t.setPlaceholder(new Label(text));
    }

    public static void info(String title, String message) {
        show(Alert.AlertType.INFORMATION, title, message);
    }

    public static void error(String title, String message) {
        show(Alert.AlertType.ERROR, title, message);
    }

    private static void show(Alert.AlertType type, String title, String message) {
        Alert a = new Alert(type, message, ButtonType.OK);
        a.setTitle(title);
        a.setHeaderText(title);
        a.showAndWait();
    }

    /** Confirmación Sí/No; el botón por defecto es "No". */
    public static boolean confirm(String title, String message) {
        ButtonType yes = new ButtonType("Sí", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        ButtonType no = new ButtonType("No", javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, message, yes, no);
        a.setTitle(title);
        a.setHeaderText(title);
        return a.showAndWait().filter(b -> b == yes).isPresent();
    }

    /** Pide un texto; vuelve a preguntar si es obligatorio y se deja vacío. Empty = cancelado. */
    public static Optional<String> ask(String title, String prompt, boolean required) {
        while (true) {
            TextInputDialog d = new TextInputDialog();
            d.setTitle(title);
            d.setHeaderText(title);
            d.setContentText(prompt);
            Optional<String> r = d.showAndWait();
            if (r.isEmpty()) {
                return Optional.empty();
            }
            if (!required || !r.get().isBlank()) {
                return Optional.of(r.get().trim());
            }
            error(title, "Este dato es obligatorio.");
        }
    }

    public static String describe(Throwable t) {
        String m = t.getMessage();
        return m == null || m.isBlank() ? t.getClass().getSimpleName() : m;
    }
}
