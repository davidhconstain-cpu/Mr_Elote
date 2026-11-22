package com.vivanetpay.parsing;

import com.vivanetpay.domain.EventSource;
import com.vivanetpay.domain.RawEvent;

import java.util.List;

/**
 * Notificaciones push de Nequi capturadas por MacroDroid (source ANDROID_NEQUI).
 * <p>
 * Plantillas v1: "NOMBRE te envió $X" (con o sin "¡Te llegó plata!"), "Recibiste $X de NOMBRE",
 * "Te enviaron $X". Descarta envíos propios, cobros/solicitudes, códigos, recordatorios y promociones.
 */
public final class NequiPushParser extends AbstractTemplateParser {
    public static final String ID = "nequi-android-v1";
    public static final String VERSION = "1.0.0";

    private static final String VERB = "te\\s+(?:envio|transfirio|giro)\\s+(?:plata\\s+)?(?:por\\s+|de\\s+|la\\s+suma\\s+de\\s+)?";

    private static final List<Template> TEMPLATES = List.of(
            Template.of("nombre-te-envio", NAME_BEFORE + VERB + AMT),
            Template.of("recibiste-de-nombre", "recibiste\\s+(?:un\\s+(?:pago|giro|envio)\\s+(?:por\\s+|de\\s+)?)?" + AMT
                    + "\\s+de\\s+(?<name>" + NAME_CHARS + ")" + NAME_END),
            Template.of("te-enviaron", "te\\s+enviaron\\s+(?:por\\s+)?" + AMT));

    private static final List<Reject> REJECTS = List.of(
            Reject.of("\\b(?:enviaste|pagaste|retiraste|recargaste|transferiste|compraste|sacaste|giraste)\\b", "movimiento propio"),
            Reject.of("te\\s+(?:pidio|pide|esta\\s+pidiendo|cobro|cobra|solicito|solicita)\\b|solicitud\\s+de\\s+(?:dinero|plata|pago)|"
                    + "cobro\\s+pendiente|\\bcobro\\b", "cobro o solicitud de dinero"),
            Reject.of("\\bcodigo\\b|\\bclave\\b|\\bcontrasena\\b|\\btoken\\b|no\\s+(?:lo\\s+)?compartas|\\botp\\b", "código de seguridad"),
            Reject.of("recordatorio|recuerda|no\\s+olvides|\\bvence\\b|\\bvencimiento\\b", "recordatorio"),
            Reject.of("\\bpromo|\\boferta|descuento|\\bgana\\b|\\bgano\\b|sorteo|\\bpremio|\\bcashback\\b|\\bbono\\b", "promoción"));

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String version() {
        return VERSION;
    }

    @Override
    public int priority() {
        return 50;
    }

    @Override
    public boolean supports(RawEvent event) {
        return event.source() == EventSource.ANDROID_NEQUI;
    }

    @Override
    protected String bank() {
        return "Nequi";
    }

    @Override
    protected List<Template> templates() {
        return TEMPLATES;
    }

    @Override
    protected List<Reject> rejects() {
        return REJECTS;
    }

    @Override
    protected boolean checkMultipleAmounts() {
        return true;
    }

    @Override
    protected boolean checkLinks() {
        return true;
    }

    @Override
    protected Origin verifyOrigin(RawEvent event) {
        String pkg = event.sender();
        if (pkg != null && !pkg.isBlank() && !fold(pkg).contains("nequi")) {
            return new Origin(CONF_LOW, "origen de la notificación no verificado (" + pkg + ")");
        }
        return Origin.OK;
    }
}
