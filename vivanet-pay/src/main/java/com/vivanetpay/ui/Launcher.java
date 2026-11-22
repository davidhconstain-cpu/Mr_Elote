package com.vivanetpay.ui;

import com.vivanetpay.app.AppConfig;

/**
 * Punto de entrada para empaquetado (jpackage/fat-jar): clase SIN heredar de {@code Application}, para que el
 * lanzador de JavaFX no exija módulos en el classpath. Fija las propiedades que deben existir antes del primer log.
 */
public final class Launcher {
    private Launcher() {
    }

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "false"); // necesario para java.awt.SystemTray
        if (System.getProperty(AppConfig.LOG_DIR_PROPERTY) == null) {
            System.setProperty(AppConfig.LOG_DIR_PROPERTY, AppConfig.load().logsDir().toString());
        }
        javafx.application.Application.launch(MainApp.class, args);
    }
}
