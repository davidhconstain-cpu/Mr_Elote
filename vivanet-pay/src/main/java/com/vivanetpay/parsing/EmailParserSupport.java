package com.vivanetpay.parsing;

import com.vivanetpay.domain.RawEvent;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Verificación de remitente de correo (IDD 8.2: el texto no es evidencia suficiente). */
final class EmailParserSupport {
    private static final Pattern ADDRESS = Pattern.compile("([\\w.+%-]+)@([A-Za-z0-9.-]+)");

    private EmailParserSupport() {
    }

    static Set<String> normalizeDomains(Collection<String> domains) {
        return domains.stream().map(d -> d.trim().toLowerCase(Locale.ROOT)).filter(d -> !d.isEmpty())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /** Dominio de la última dirección del From (la real, no la del nombre visible), en minúsculas, o null. */
    static String senderDomain(RawEvent event) {
        String from = event.sender();
        if (from == null || from.isBlank()) {
            from = event.headers().getOrDefault("From", event.headers().get("from"));
        }
        if (from == null) {
            return null;
        }
        Matcher m = ADDRESS.matcher(from);
        String domain = null;
        while (m.find()) {
            domain = m.group(2);
        }
        if (domain == null) {
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
