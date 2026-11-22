package com.vivanetpay.parsing;

import com.vivanetpay.domain.EventSource;
import com.vivanetpay.domain.RawEvent;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Correos de Nequi leídos por Gmail. Verifica el dominio del remitente (configurable); si no coincide la
 * confianza queda en 0.3 con motivo "remitente no verificado".
 */
public final class NequiEmailParser extends AbstractTemplateParser {
    public static final String ID = "nequi-email-v1";
    public static final String VERSION = "1.0.0";
    public static final Set<String> DEFAULT_DOMAINS = Set.of("nequi.com.co", "nequi.com");

    private static final String VERB = "te\\s+(?:envio|transfirio|giro)\\s+(?:plata\\s+)?(?:por\\s+|de\\s+|la\\s+suma\\s+de\\s+)?";

    private static final List<Template> TEMPLATES = List.of(
            Template.of("nombre-te-envio", NAME_BEFORE + VERB + AMT),
            Template.of("recibiste-de-nombre", "recibiste\\s+(?:un\\s+(?:pago|giro|envio)\\s+(?:por\\s+|de\\s+)?)?" + AMT
                    + "\\s+de\\s+(?<name>" + NAME_CHARS + ")" + NAME_END),
            Template.of("te-enviaron", "te\\s+enviaron\\s+(?:por\\s+)?" + AMT));

    private static final List<Reject> REJECTS = List.of(
            Reject.of("\\b(?:enviaste|pagaste|retiraste|recargaste|transferiste|compraste|realizaste)\\b", "movimiento propio"),
            Reject.of("te\\s+(?:pidio|pide|esta\\s+pidiendo|cobro|solicito)\\b|solicitud\\s+de\\s+(?:dinero|plata|pago)|cobro\\s+pendiente",
                    "cobro o solicitud de dinero"),
            Reject.of("codigo\\s+de\\s+(?:seguridad|verificacion)|clave\\s+(?:temporal|dinamica)", "código de seguridad"));

    private final Set<String> allowedDomains;

    public NequiEmailParser() {
        this(DEFAULT_DOMAINS);
    }

    public NequiEmailParser(Collection<String> allowedDomains) {
        this.allowedDomains = EmailParserSupport.normalizeDomains(allowedDomains);
    }

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
        return 40;
    }

    @Override
    public boolean supports(RawEvent event) {
        return event.source() == EventSource.GMAIL
                && (EmailParserSupport.domainAllowed(EmailParserSupport.senderDomain(event), allowedDomains)
                || fold(joinText(event) + " " + (event.sender() == null ? "" : event.sender())).contains("nequi"));
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
    protected boolean checkLinks() {
        return false;
    }

    @Override
    protected Origin verifyOrigin(RawEvent event) {
        return EmailParserSupport.verify(event, allowedDomains);
    }
}
