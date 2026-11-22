package com.vivanetpay.domain;

/** Estado de conexión de un conector (RF-017, RF-025). */
public enum ConnectorStatus {
    CONECTADO("Conectado"),
    DESCONECTADO("Desconectado"),
    REQUIERE_AUTORIZACION("Requiere autorización"),
    ERROR("Error"),
    PAUSADO("Pausado");

    private final String label;

    ConnectorStatus(String label) {
        this.label = label;
    }

    /** Texto en español claro para la interfaz (RNF-009). */
    public String label() {
        return label;
    }

    /** @return true si el estado requiere atención del usuario (alerta visual/voz). */
    public boolean needsAttention() {
        return this == DESCONECTADO || this == REQUIERE_AUTORIZACION || this == ERROR;
    }
}
