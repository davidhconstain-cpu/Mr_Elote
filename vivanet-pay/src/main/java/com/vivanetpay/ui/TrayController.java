package com.vivanetpay.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.AWTException;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.util.List;

/**
 * Icono de bandeja (java.awt.SystemTray) con el menú de IDD §9.2: Abrir, Pausar/reanudar captura,
 * Silenciar/activar voz, Estado de conectores y Salir. Las acciones se entregan a {@link Actions} (el llamador
 * debe pasarlas al hilo de JavaFX). Si la bandeja no está soportada, {@link #install()} devuelve false y la
 * aplicación degrada a "cerrar = salir con confirmación".
 */
public final class TrayController {
    private static final Logger log = LoggerFactory.getLogger(TrayController.class);

    /** Acciones del menú. Se invocan en el hilo de AWT. */
    public interface Actions {
        void open();

        void toggleCapture();

        void toggleMute();

        void exit();
    }

    private final Actions actions;
    private TrayIcon icon;
    private final MenuItem captureItem = new MenuItem("Pausar captura");
    private final MenuItem muteItem = new MenuItem("Silenciar voz");
    private final java.awt.Menu connectorsMenu = new java.awt.Menu("Estado de conectores");

    public TrayController(Actions actions) {
        this.actions = actions;
    }

    public static boolean isSupported() {
        try {
            return !java.awt.GraphicsEnvironment.isHeadless() && SystemTray.isSupported();
        } catch (Throwable t) {
            return false;
        }
    }

    /** @return true si el icono quedó instalado */
    public boolean install() {
        if (!isSupported()) {
            log.info("Bandeja del sistema no soportada: se cerrará la aplicación al cerrar la ventana");
            return false;
        }
        PopupMenu menu = new PopupMenu();
        MenuItem open = new MenuItem("Abrir Vivanet Pay");
        open.addActionListener(e -> actions.open());
        captureItem.addActionListener(e -> actions.toggleCapture());
        muteItem.addActionListener(e -> actions.toggleMute());
        MenuItem exit = new MenuItem("Salir");
        exit.addActionListener(e -> actions.exit());
        connectorsMenu.add(disabled("Sin información"));
        menu.add(open);
        menu.addSeparator();
        menu.add(captureItem);
        menu.add(muteItem);
        menu.add(connectorsMenu);
        menu.addSeparator();
        menu.add(exit);
        icon = new TrayIcon(AppIcons.tray(false), "Vivanet Pay", menu);
        icon.setImageAutoSize(true);
        icon.addActionListener(e -> actions.open()); // doble clic
        try {
            SystemTray.getSystemTray().add(icon);
            return true;
        } catch (AWTException | RuntimeException e) {
            log.warn("No se pudo instalar el icono de bandeja: {}", e.getMessage());
            icon = null;
            return false;
        }
    }

    /** Actualiza etiquetas y tooltip según el estado. */
    public void update(boolean capturePaused, boolean muted, List<String> connectorLines) {
        if (icon == null) {
            return;
        }
        captureItem.setLabel(capturePaused ? "Reanudar captura" : "Pausar captura");
        muteItem.setLabel(muted ? "Activar voz" : "Silenciar voz");
        connectorsMenu.removeAll();
        if (connectorLines.isEmpty()) {
            connectorsMenu.add(disabled("Sin conectores activos"));
        }
        for (String l : connectorLines) {
            connectorsMenu.add(disabled(l));
        }
        icon.setImage(AppIcons.tray(capturePaused || muted));
        String tip = "Vivanet Pay" + (capturePaused ? " - captura en pausa" : "") + (muted ? " - voz silenciada" : "");
        icon.setToolTip(tip);
    }

    /** Notificación visual (voz silenciada o con fallo, conector con problema). */
    public void notify(String title, String message, boolean error) {
        if (icon != null) {
            icon.displayMessage(title, message, error ? TrayIcon.MessageType.WARNING : TrayIcon.MessageType.INFO);
        }
    }

    public void remove() {
        if (icon != null) {
            SystemTray.getSystemTray().remove(icon);
            icon = null;
        }
    }

    private static MenuItem disabled(String label) {
        MenuItem m = new MenuItem(label);
        m.setEnabled(false);
        return m;
    }
}
