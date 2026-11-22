package com.vivanetpay.ui;

import com.vivanetpay.app.AppContext;
import com.vivanetpay.app.Bootstrap;
import com.vivanetpay.domain.ConnectorState;
import com.vivanetpay.domain.ConnectorStatus;
import com.vivanetpay.integration.Connector;

import java.util.Collection;
import java.util.List;

/** Dependencias compartidas por las pantallas. */
public final class UiContext {
    /** Servicios de la ventana principal que las pantallas necesitan. */
    public interface Shell {
        void navigate(String screenId);

        void banner(String message);

        void requestExit();

        void onPreferencesChanged();
    }

    public final AppContext app;
    public final Bootstrap.Result boot;
    public final UiPreferences prefs;
    public final WindowsStartup startup = new WindowsStartup();
    public Shell shell;

    public UiContext(Bootstrap.Result boot) {
        this.boot = boot;
        this.app = boot.context();
        this.prefs = new UiPreferences(app.settingsRepository());
    }

    /** Usuario que firma las decisiones de revisión (auditoría). */
    public String actor() {
        String u = System.getProperty("user.name");
        return u == null || u.isBlank() ? "operador" : u;
    }

    public List<ConnectorState> connectorStates() {
        return app.connectorRegistry().all().stream().map(Connector::state).toList();
    }

    /** true si hay conectores y todos están en pausa. */
    public boolean capturePaused() {
        Collection<Connector> all = app.connectorRegistry().all();
        return !all.isEmpty() && all.stream().allMatch(c -> c.state().status() == ConnectorStatus.PAUSADO);
    }

    /** Pausa o reanuda todos los conectores (bloqueante). */
    public void setCapturePaused(boolean paused) {
        for (Connector c : app.connectorRegistry().all()) {
            if (paused) {
                c.pause();
            } else if (c.state().status() == ConnectorStatus.PAUSADO) {
                c.resume();
            }
        }
    }
}
