package com.vivanetpay.ui;

import com.vivanetpay.persistence.SettingsRepository;

/** Preferencias de la interfaz persistidas en la tabla {@code app_setting}. Accede a la base: fuera del hilo de UI. */
public final class UiPreferences {
    public static final String MINIMIZE_TO_TRAY = "ui.minimizeToTray";
    public static final String START_WITH_WINDOWS = "ui.startWithWindows";
    public static final String THEME = "ui.theme";
    public static final String VOICE_ENABLED = "voice.enabled";
    public static final String VOICE_VOLUME = "voice.volume";
    public static final String VOICE_RATE = "voice.rate";

    private final SettingsRepository repo;

    public UiPreferences(SettingsRepository repo) {
        this.repo = repo;
    }

    public boolean getBoolean(String key, boolean def) {
        return repo.get(key).map(Boolean::parseBoolean).orElse(def);
    }

    public String getString(String key, String def) {
        return repo.get(key).orElse(def);
    }

    public int getInt(String key, int def) {
        try {
            return repo.get(key).map(Integer::parseInt).orElse(def);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public void put(String key, String value) {
        repo.put(key, value);
    }

    public void put(String key, boolean value) {
        repo.put(key, Boolean.toString(value));
    }

    public void put(String key, int value) {
        repo.put(key, Integer.toString(value));
    }
}
