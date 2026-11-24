package com.vivanetpay.validation;

import com.vivanetpay.domain.AnnouncementKind;
import com.vivanetpay.domain.EventSource;
import com.vivanetpay.domain.EventStatus;
import com.vivanetpay.parsing.BancolombiaEmailParser;
import com.vivanetpay.parsing.EmailParserSupport;
import com.vivanetpay.parsing.NequiEmailParser;
import com.vivanetpay.parsing.NequiPushParser;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Política por defecto desde la decisión DP-01 (propietario, 24/11/2026): confirmación automática de pagos que
 * provienen de fuentes verificadas, bajo reglas estrictas. Cualquier caso que no las cumpla sigue el flujo
 * conservador de {@link ConservativeValidationPolicy} (por verificar / revisión / rechazo).
 * <p>
 * Riesgo aceptado: una notificación o un correo NO es un comprobante bancario. Si {@code policy.automaticValidation}
 * es {@code false}, esta política se comporta exactamente como la conservadora.
 * <ul>
 *   <li><b>{@value #RULE_ANDROID_NEQUI}</b>: fuente ANDROID_NEQUI, llegó por el webhook autenticado (cabecera interna
 *       {@code webhook.auth}, que solo pone el receptor tras autenticar), parser {@code nequi-android}, confianza
 *       &ge; 0.9, importe &gt; 0, paquete Android (si viene) contiene "nequi" y sin duplicado/ambigüedad.</li>
 *   <li><b>{@value #RULE_GMAIL}</b>: fuente GMAIL, remitente de un dominio esperado del banco detectado, Authentication-Results
 *       (o ARC) con dkim=pass y (spf=pass o dmarc=pass) alineados con ese dominio, parser de correo con confianza
 *       &ge; 0.9, importe &gt; 0 y sin duplicado/ambigüedad. Sin cabecera de autenticación no se confirma.</li>
 * </ul>
 * Los eventos históricos pueden quedar confirmados pero nunca se anuncian por voz (lo garantiza la cola de voz).
 */
public final class TrustedSourceValidationPolicy implements ValidationPolicy {
    public static final String ID = "fuentes-verificadas-dp01-v1";
    public static final String RULE_ANDROID_NEQUI = "AUTO_ANDROID_NEQUI";
    public static final String RULE_GMAIL = "AUTO_GMAIL_DKIM";
    /** Cabecera interna puesta por el receptor webhook solo después de autenticar la solicitud. */
    public static final String HEADER_WEBHOOK_AUTH = "webhook.auth";
    public static final double MIN_AUTO_CONFIDENCE = 0.9;

    /** Dominios de correo esperados por entidad (clave en minúsculas, tal como la normaliza el parser). */
    static final Map<String, Set<String>> BANK_DOMAINS = Map.of(
            "nequi", Set.of("nequi.com.co"),
            "bancolombia", Set.of("bancolombia.com.co"));

    private volatile ValidationPolicyConfig config;
    private final ConservativeValidationPolicy conservative;

    public TrustedSourceValidationPolicy() {
        this(ValidationPolicyConfig.defaults());
    }

    public TrustedSourceValidationPolicy(ValidationPolicyConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.conservative = new ConservativeValidationPolicy(new ValidationPolicyConfig(false, Set.of(),
                config.minConfidence(), config.announceUnconfirmed(), config.announceOnManualValidation()));
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public ValidationPolicyConfig config() {
        return config;
    }

    /**
     * Activa o desactiva la confirmación automática en caliente (interruptor de Configuración). Afecta solo a los
     * eventos que lleguen después; los ya confirmados conservan su estado.
     */
    public void setAutomaticValidationEnabled(boolean enabled) {
        ValidationPolicyConfig c = config;
        config = new ValidationPolicyConfig(enabled, c.trustedSources(), c.minConfidence(), c.announceUnconfirmed(),
                c.announceOnManualValidation());
    }

    @Override
    public PolicyDecision decide(PolicyInput in) {
        ValidationPolicyConfig config = this.config;
        PolicyDecision base = conservative.decide(in);
        if (base.status() != EventStatus.PARSEADO || !config.automaticValidationEnabled()
                || !config.trustedSources().contains(in.raw().source())) {
            return base;
        }
        Check c = switch (in.raw().source()) {
            case ANDROID_NEQUI -> android(in, config);
            case GMAIL -> gmail(in, config);
            default -> new Check(null, "la fuente no tiene regla de confirmación automática");
        };
        if (c.rule() == null) {
            return new PolicyDecision(base.status(), base.announcement(),
                    base.reason() + " [sin confirmación automática: " + c.detail() + "]");
        }
        return new PolicyDecision(EventStatus.VALIDADO_POR_FUENTE, AnnouncementKind.PAGO_CONFIRMADO,
                "Confirmado automáticamente por fuente verificada (regla " + c.rule() + "): " + c.detail(), c.rule());
    }

    private record Check(String rule, String detail) {
    }

    private Check android(PolicyInput in, ValidationPolicyConfig config) {
        var raw = in.raw();
        var p = in.parse();
        String auth = raw.headers().get(HEADER_WEBHOOK_AUTH);
        if (auth == null || auth.isBlank()) {
            return new Check(null, "no llegó por el webhook autenticado");
        }
        if (!NequiPushParser.ID.equals(p.parserId())) {
            return new Check(null, "parser distinto de nequi-android");
        }
        if (p.confidence() < Math.max(MIN_AUTO_CONFIDENCE, config.minConfidence())) {
            return new Check(null, "confianza insuficiente");
        }
        if (p.amount() == null || p.amount() <= 0) {
            return new Check(null, "importe no positivo");
        }
        String pkg = raw.sender();
        if (pkg != null && !pkg.isBlank() && !pkg.toLowerCase(Locale.ROOT).contains("nequi")) {
            return new Check(null, "el paquete Android no es de Nequi");
        }
        return new Check(RULE_ANDROID_NEQUI, "webhook autenticado (" + auth + "), parser " + p.parserId()
                + ", confianza " + String.format(Locale.ROOT, "%.2f", p.confidence()));
    }

    private Check gmail(PolicyInput in, ValidationPolicyConfig config) {
        var raw = in.raw();
        var p = in.parse();
        if (!NequiEmailParser.ID.equals(p.parserId()) && !BancolombiaEmailParser.ID.equals(p.parserId())) {
            return new Check(null, "parser de correo no reconocido");
        }
        if (p.confidence() < Math.max(MIN_AUTO_CONFIDENCE, config.minConfidence())) {
            return new Check(null, "confianza insuficiente");
        }
        if (p.amount() == null || p.amount() <= 0) {
            return new Check(null, "importe no positivo");
        }
        Set<String> expected = p.bank() == null ? null : BANK_DOMAINS.get(p.bank().toLowerCase(Locale.ROOT));
        if (expected == null) {
            return new Check(null, "entidad sin dominio de correo esperado");
        }
        String domain = EmailParserSupport.senderDomain(raw);
        if (!EmailParserSupport.domainAllowed(domain, expected)) {
            return new Check(null, "el remitente no es de un dominio esperado del banco");
        }
        EmailAuthentication.Result auth = EmailAuthentication.evaluate(raw.headers(), expected);
        if (!auth.passed()) {
            return new Check(null, auth.detail());
        }
        return new Check(RULE_GMAIL, "remitente " + domain + "; " + auth.detail());
    }
}
