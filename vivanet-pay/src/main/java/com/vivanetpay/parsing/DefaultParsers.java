package com.vivanetpay.parsing;

import java.util.Collection;
import java.util.List;

/**
 * Parsers reales del MVP (Nequi push, Nequi correo, Bancolombia correo). DaviPlata/Davivienda quedan fuera (IDD 2.3).
 * <p>
 * Uso: {@code AppContext.builder()...parsers(DefaultParsers.all())}.
 */
public final class DefaultParsers {
    private DefaultParsers() {
    }

    /** Parsers con dominios de remitente por defecto. */
    public static List<PaymentParser> all() {
        return List.of(new NequiPushParser(), new NequiEmailParser(), new BancolombiaEmailParser());
    }

    /** Parsers con dominios de correo configurados. */
    public static List<PaymentParser> all(Collection<String> nequiDomains, Collection<String> bancolombiaDomains) {
        return List.of(new NequiPushParser(), new NequiEmailParser(nequiDomains),
                new BancolombiaEmailParser(bancolombiaDomains));
    }

    public static ParserRegistry registry() {
        return new ParserRegistry(all());
    }
}
