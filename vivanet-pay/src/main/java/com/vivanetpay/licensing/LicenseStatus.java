package com.vivanetpay.licensing;

/** Estado local de la licencia. */
public enum LicenseStatus {
    /** Dentro de los 14 días de prueba. */
    PRUEBA_ACTIVA("Prueba activa"),
    /** Prueba vencida. Qué hacer después es DP-07. */
    PRUEBA_VENCIDA("Prueba vencida"),
    /** Se detectó reloj del equipo retrocedido; requiere verificación (R-09). Es persistente hasta resolver DP-07. */
    SOSPECHOSO("Fecha del equipo inconsistente"),
    /** Reservado para licencia comercial activa (DP-07 pendiente; nunca se asigna en esta versión). */
    ACTIVA("Licencia activa");

    private final String label;

    LicenseStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** @return true si este estado permitiría capturar en modo estricto ({@code license.enforce=true}). */
    public boolean allowsCapture() {
        return this == PRUEBA_ACTIVA || this == ACTIVA;
    }
}
