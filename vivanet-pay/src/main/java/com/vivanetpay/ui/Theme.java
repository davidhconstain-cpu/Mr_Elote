package com.vivanetpay.ui;

import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.DialogPane;

import java.net.URL;

/**
 * Tema claro/oscuro. Sigue el esquema de color del sistema (Windows: "Modo de la aplicación") mediante
 * {@link Platform#getPreferences()} (JavaFX 22+) y lo refleja con la clase CSS {@code dark} en la raíz de cada
 * escena; los colores viven como "looked-up colors" ({@code -vp-*}) en {@code /ui/vivanet.css}.
 * <p>
 * Debe usarse desde el hilo de JavaFX.
 */
public final class Theme {
    static final String DARK_CLASS = "dark";
    private static final String STYLESHEET = "/ui/vivanet.css";

    private static final ReadOnlyBooleanWrapper DARK = new ReadOnlyBooleanWrapper(false);
    private static boolean listening;

    private Theme() {
    }

    /** Traduce el esquema del sistema: solo {@link ColorScheme#DARK} es oscuro (null = claro, valor por defecto). */
    public static boolean isDark(ColorScheme scheme) {
        return scheme == ColorScheme.DARK;
    }

    public static ReadOnlyBooleanProperty darkProperty() {
        return DARK.getReadOnlyProperty();
    }

    /** Fuerza el tema (herramienta de capturas y pruebas). */
    static void setDark(boolean dark) {
        DARK.set(dark);
    }

    /** Empieza a seguir el esquema del sistema (idempotente). */
    public static void followSystem() {
        if (listening) {
            return;
        }
        listening = true;
        Platform.Preferences prefs = Platform.getPreferences();
        DARK.set(isDark(prefs.getColorScheme()));
        prefs.colorSchemeProperty().addListener((o, was, now) -> DARK.set(isDark(now)));
    }

    /** Aplica hoja de estilos y clase de tema a una escena; se mantiene al día si cambia el tema del sistema. */
    public static void apply(Scene scene) {
        URL css = Theme.class.getResource(STYLESHEET);
        if (css != null && !scene.getStylesheets().contains(css.toExternalForm())) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        track(scene.getRoot());
        scene.rootProperty().addListener((o, old, now) -> track(now));
    }

    /** Aplica el tema a un cuadro de diálogo (los diálogos tienen su propia escena y no heredan la principal). */
    public static void apply(DialogPane pane) {
        URL css = Theme.class.getResource(STYLESHEET);
        if (css != null) {
            pane.getStylesheets().add(css.toExternalForm());
        }
        track(pane);
    }

    /**
     * El listener lo retiene la propia raíz ({@code getProperties}) y la propiedad global solo lo ve por referencia
     * débil: al cerrarse un diálogo o cambiar de raíz no queda nada colgando.
     */
    private static void track(Parent root) {
        setDarkClass(root, DARK.get());
        ChangeListener<Boolean> l = (o, was, now) -> setDarkClass(root, now);
        root.getProperties().put("vivanet.themeListener", l);
        DARK.addListener(new WeakChangeListener<>(l));
    }

    private static void setDarkClass(Parent root, boolean dark) {
        if (dark) {
            if (!root.getStyleClass().contains(DARK_CLASS)) {
                root.getStyleClass().add(DARK_CLASS);
            }
        } else {
            root.getStyleClass().remove(DARK_CLASS);
        }
    }
}
