package com.vivanetpay.parsing;

import com.vivanetpay.domain.RawEvent;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Verificación de remitente de correo (IDD 8.2: el texto no es evidencia suficiente). */
final class EmailParserSupport {

    private EmailParserSupport() {
    }

    static Set<String> normalizeDomains(Collection<String> domains) {
        return domains.stream().map(d -> d.trim().toLowerCase(Locale.ROOT)).filter(d -> !d.isEmpty())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static final Pattern DOMAIN = Pattern.compile("[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?");

    /**
     * Dominio REAL del remitente: el de la dirección entre {@code <...>} al final del From o, si no hay, el de todo el
     * valor (después de la ÚLTIMA {@code @}). Un nombre visible con una dirección legítima
     * ({@code "alertas@banco.com" <x@evil.com>}), una parte local entre comillas ({@code "x@banco.com"@evil.com}) o
     * comentarios finales no engañan: ante cualquier forma rara el dominio es nulo y el origen queda SIN verificar.
     */
    static String senderDomain(RawEvent event) {
        String from = event.sender();
        if (from == null || from.isBlank()) {
            from = event.headers().getOrDefault("From", event.headers().get("from"));
        }
        if (from == null) {
            return null;
        }
        String f = from.strip();
        int lt = f.lastIndexOf('<');
        int gt = f.lastIndexOf('>');
        String addr = lt >= 0 && gt > lt && f.substring(gt + 1).isBlank() ? f.substring(lt + 1, gt).strip() : f;
        int at = addr.lastIndexOf('@');
        if (at <= 0 || at == addr.length() - 1) {
            return null;
        }
        String domain = addr.substring(at + 1);
        if (!DOMAIN.matcher(domain).matches()) {
            return null;
        }
        domain = domain.toLowerCase(Locale.ROOT);
        return domain.endsWith(".") ? domain.substring(0, domain.length() - 1) : domain;
    }

    static boolean domainAllowed(String domain, Set<String> allowed) {
        if (domain == null) {
            return false;
        }
        for (String a : allowed) {
            if (domain.equals(a) || domain.endsWith("." + a)) {
                return true;
            }
        }
        return false;
    }

    static AbstractTemplateParser.Origin verify(RawEvent event, Set<String> allowed) {
        String domain = senderDomain(event);
        if (!domainAllowed(domain, allowed)) {
            return new AbstractTemplateParser.Origin(0.3, "remitente no verificado ("
                    + (domain == null ? "sin dirección" : domain) + ")");
        }
        String auth = event.headers().entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase("Authentication-Results"))
                .map(java.util.Map.Entry::getValue).findFirst().orElse("").toLowerCase(Locale.ROOT);
        if (auth.contains("dkim=fail") || auth.contains("spf=fail") || auth.contains("dmarc=fail")) {
            return new AbstractTemplateParser.Origin(0.3, "remitente no verificado (autenticación SPF/DKIM fallida)");
        }
        return AbstractTemplateParser.Origin.OK;
    }
}
