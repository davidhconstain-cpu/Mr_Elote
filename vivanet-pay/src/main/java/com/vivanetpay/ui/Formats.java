package com.vivanetpay.ui;

import com.vivanetpay.domain.AppTime;
import com.vivanetpay.domain.ConnectorStatus;
import com.vivanetpay.domain.CopAmount;
import com.vivanetpay.domain.EventSource;
import com.vivanetpay.domain.EventStatus;

import java.time.Instant;

/**
 * Textos de presentación en español. Los estados llevan un símbolo además del texto para no depender
 * solo del color (IDD §9.4). Importes: {@code $125.000} sin decimales. Horas: America/Bogota.
 */
public final class Formats {
    public static final String NONE = "—";

    private Formats() {
    }

    public static String money(long pesos) {
        return CopAmount.format(pesos);
    }

    public static String money(Long pesos) {
        return pesos == null ? "Sin importe" : CopAmount.format(pesos);
    }

    public static String time(Instant instant) {
        return instant == null ? NONE : AppTime.format(instant);
    }

    public static String count(int n) {
        return n == 1 ? "1 movimiento" : n + " movimientos";
    }

    /** Etiqueta de estado de evento con símbolo. */
    public static String status(EventStatus s) {
        if (s == null) {
            return NONE;
        }
        return switch (s) {
            case RECIBIDO -> "○ Recibido";
            case PARSEADO -> "⚠ Por verificar";
            case PENDIENTE_REVISION -> "⚠ Pendiente de revisión";
            case VALIDADO_POR_FUENTE -> "✔ Confirmado";
            case ANUNCIADO -> "✔ Confirmado y anunciado";
            case DUPLICADO -> "≡ Duplicado";
            case RECHAZADO -> "✖ Rechazado";
            case ERROR -> "✖ Error";
        };
    }

    public static String source(EventSource s) {
        if (s == null) {
            return NONE;
        }
        return switch (s) {
            case GMAIL -> "Gmail";
            case ANDROID_NEQUI -> "Android (Nequi)";
            case ANDROID_OTHER -> "Android (otra app)";
            case MANUAL -> "Manual";
        };
    }

    /** Etiqueta de estado de conector con símbolo. */
    public static String connectorStatus(ConnectorStatus s) {
        return switch (s) {
            case CONECTADO -> "✔ Conectado";
            case DESCONECTADO -> "✖ Desconectado";
            case REQUIERE_AUTORIZACION -> "⚠ Requiere autorización";
            case ERROR -> "✖ Error";
            case PAUSADO -> "⏸ Pausado";
        };
    }

    /** Acción recomendada en español claro para cada estado de conector (RNF-009). */
    public static String recommendedAction(ConnectorStatus s) {
        return switch (s) {
            case CONECTADO -> "No necesitas hacer nada.";
            case DESCONECTADO -> "Revisa la conexión a Internet y del teléfono; se reintentará solo.";
            case REQUIERE_AUTORIZACION -> "Pulsa \"Autorizar\" para volver a conectar la cuenta.";
            case ERROR -> "Revisa Diagnóstico; si persiste, exporta el paquete de diagnóstico y contacta soporte.";
            case PAUSADO -> "La captura está en pausa. Pulsa \"Reanudar\" para volver a recibir pagos.";
        };
    }

    /**
     * Motivo legible para la tabla (el texto técnico completo queda en el detalle). Los motivos desconocidos se
     * muestran tal cual.
     */
    public static String friendlyReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "";
        }
        String r = reason.trim();
        if (r.startsWith("Ninguna plantilla") || r.contains("no reconocido") || r.startsWith("Formato no reconocido")) {
            return "No se reconoció el formato del mensaje";
        }
        if (r.startsWith("Importe malformado")) {
            return "No se pudo leer el importe del mensaje";
        }
        if (r.startsWith("Posible pago: requiere verificación manual")) {
            return "Posible pago: verifícalo en tu banco";
        }
        if (r.startsWith("Posible duplicado del evento")) {
            return "Posible duplicado de un pago ya registrado";
        }
        return r;
    }
}
