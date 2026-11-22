package com.vivanetpay.domain;

/** Tipo de conector. El identificador de configuración es {@link #configKey()}. */
public enum ConnectorType {
    GMAIL("gmail"),
    ANDROID_WEBHOOK("android");

    private final String configKey;

    ConnectorType(String configKey) {
        this.configKey = configKey;
    }

    /** Clave usada en config.properties ({@code connectors.enabled=gmail,android}). */
    public String configKey() {
        return configKey;
    }

    public static ConnectorType fromConfigKey(String key) {
        for (ConnectorType t : values()) {
            if (t.configKey.equalsIgnoreCase(key.trim()) || t.name().equalsIgnoreCase(key.trim())) {
                return t;
            }
        }
        throw new IllegalArgumentException("Tipo de conector desconocido: " + key);
    }
}
