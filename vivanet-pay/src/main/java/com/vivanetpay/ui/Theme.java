package com.vivanetpay.ui;

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
 * Tema claro/oscuro. El modo ({@link Mode}) es automático (sigue al sistema), claro u oscuro, y se guarda en las
 * preferencias de la UI. En automático se usa el modo de aplicaciones del sistema ({@link SystemTheme}: en
 * JavaFX 22+ se sigue en vivo por reflexión; en la línea LTS 21 se lee una vez el registro de Windows al
 * arrancar). El resultado se refleja con la clase CSS {@code dark} en la raíz de cada escena; los colores viven
 * como "looked-up colors" ({@code -vp-*}) en {@code /ui/vivanet.css}.
 * <p>
 * Debe usarse desde el hilo de JavaFX.
 */
public final class Theme {
    /** Preferencia del usuario. */
    public enum Mode {
        AUTOMATICO("Automático"), CLARO("Claro"), OSCURO("Oscuro");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        /** Valor guardado -> modo; cualquier valor desconocido o nulo = automático. */
        public static Mode parse(String stored) {
            if (stored != null) {
                for (Mode m : values()) {
                    if (m.name().equalsIgnoreCase(stored.trim())) {
                        return m;
                    }
                }
            }
            return AUTOMATICO;
        }
    }

    static final String DARK_CLASS = "dark";
    private static final String STYLESHEET = "/ui/vivanet.css";

    private static final ReadOnlyBooleanWrapper DARK = new ReadOnlyBooleanWrapper(false);
    private static Mode mode = Mode.AUTOMATICO;
    private static boolean systemDark;
    private static boolean listening;

    private Theme() {
    }

    /** Regla pura: ¿hay que usar oscuro con este modo y este estado del sistema? */
    public static boolean resolve(Mode mode, boolean systemDark) {
        return switch (mode) {
            case OSCURO -> true;
            case CLARO -> false;
            case AUTOMATICO -> systemDark;
        };
    }

    public static ReadOnlyBooleanProperty darkProperty() {
        return DARK.getReadOnlyProperty();
    }

    public static Mode mode() {
        return mode;
    }

    public static void setMode(Mode m) {
        mode = m == null ? Mode.AUTOMATICO : m;
        DARK.set(resolve(mode, systemDark));
    }

    /** Fija el estado del sistema (herramienta de capturas y pruebas). */
    static void setSystemDark(boolean dark) {
        systemDark = dark;
        DARK.set(resolve(mode, systemDark));
    }

    /** Empieza a seguir el modo del sistema (idempotente). Solo bloquea en un hilo virtual. */
    public static void followSystem() {
        if (listening) {
            return;
        }
        listening = true;
        if (!SystemTheme.listenWithPlatformPreferences(Theme::setSystemDark)) {
            Thread.ofVirtual().name("vivanet-theme").start(() -> {
                boolean dark = SystemTheme.readWindowsDark();
                if (dark) {
                    Platform.runLater(() -> setSystemDark(true));
                }
            });
        }
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
