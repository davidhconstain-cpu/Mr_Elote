package com.vivanetpay.parsing;

import com.vivanetpay.domain.EventSource;
import com.vivanetpay.domain.RawEvent;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Alertas por correo de Bancolombia (transferencias recibidas) leídas por Gmail. Verifica el dominio del
 * remitente (configurable); si no coincide la confianza queda en 0.3 con motivo "remitente no verificado".
 */
public final class BancolombiaEmailParser extends AbstractTemplateParser {
    public static final String ID = "bancolombia-email-v1";
    public static final String VERSION = "1.0.0";
    public static final Set<String> DEFAULT_DOMAINS = Set.of("bancolombia.com.co", "bancolombia.com");

    private static final String KIND = "(?:una?\\s+)?(?:transferencia|abono|pago|consignacion|giro)\\s+(?:recibid[oa]\\s+)?";
    private static final String BY = "(?:por\\s+(?:valor\\s+de\\s+)?|de\\s+(?:valor\\s+)?)?";

    private static final List<Template> TEMPLATES = List.of(
            Template.of("recibio-transferencia-por-de-nombre",
                    "(?:recibio|recibiste|recibimos|registro|abono)\\s+" + KIND + BY + AMT
                            + "\\s+(?:de|desde)\\s+(?<name>" + NAME_CHARS + ")" + NAME_END),
            Template.of("recibiste-transferencia-de-nombre-por",
                    "(?:recibio|recibiste|recibimos)\\s+" + KIND + "(?:de|desde)\\s+(?<name>" + NAME_CHARS
                            + ")\\s+por\\s+(?:valor\\s+de\\s+)?" + AMT),
            Template.of("nombre-te-transfirio",
                    NAME_BEFORE + "te\\s+(?:envio|transfirio|giro)\\s+(?:por\\s+|de\\s+)?" + AMT),
            Template.of("recibio-transferencia-sin-nombre",
                    "(?:recibio|recibiste|recibimos)\\s+" + KIND + BY + AMT));

    private static final List<Reject> REJECTS = List.of(
            Reject.of("\\b(?:enviaste|pagaste|retiraste|realizaste|compraste|transferiste|giraste)\\b|\\bdebit(?:o|ado)\\b|"
                    + "\\bcompra\\b|\\bretiro\\b", "movimiento propio o débito"),
            Reject.of("te\\s+(?:pidio|solicito)\\b|solicitud\\s+de\\s+(?:dinero|plata|pago)|cobro\\s+pendiente", "cobro o solicitud"),
            Reject.of("codigo\\s+de\\s+(?:seguridad|verificacion)|clave\\s+(?:temporal|dinamica)|actualiza\\s+tus\\s+datos",
                    "código/seguridad"));

    private final Set<String> allowedDomains;

    public BancolombiaEmailParser() {
        this(DEFAULT_DOMAINS);
    }

    public BancolombiaEmailParser(Collection<String> allowedDomains) {
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
                || fold(joinText(event) + " " + (event.sender() == null ? "" : event.sender())).contains("bancolombia"));
    }

    @Override
    protected String bank() {
        return "Bancolombia";
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
    protected Origin verifyOrigin(RawEvent event) {
        return EmailParserSupport.verify(event, allowedDomains);
    }
}
