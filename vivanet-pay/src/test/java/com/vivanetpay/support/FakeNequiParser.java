package com.vivanetpay.support;

import com.vivanetpay.domain.RawEvent;
import com.vivanetpay.parsing.ParseResult;
import com.vivanetpay.parsing.PaymentParser;
import com.vivanetpay.validation.AmountNormalizer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser de PRUEBA (no productivo): reconoce "NOMBRE te envió $50.000" con "Ref: X" opcional.
 * Si el texto contiene "[baja]" devuelve confianza 0.5; si contiene "[USD]" moneda USD.
 */
public final class FakeNequiParser implements PaymentParser {
    private static final Pattern P = Pattern.compile("^(.+?) te envi[oó] (\\$?\\s?-?[\\d.,]+)(?:.*?Ref: (\\S+))?.*$",
            Pattern.DOTALL);

    @Override
    public String id() {
        return "fake-nequi-test-v1";
    }

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public boolean supports(RawEvent event) {
        return event.rawText().contains("te envi");
    }

    @Override
    public ParseResult parse(RawEvent event) {
        Matcher m = P.matcher(event.rawText());
        if (!m.matches()) {
            return ParseResult.notRecognized(id(), "Plantilla de prueba no coincide");
        }
        Long amount;
        String txt = m.group(2);
        if (txt.contains("-")) {
            amount = -1L;
        } else {
            amount = AmountNormalizer.parsePesos(txt).orElse(null);
        }
        return ParseResult.builder(id())
                .amount(amount)
                .bank("Nequi")
                .sender(m.group(1).trim())
                .reference(m.group(3))
                .currency(event.rawText().contains("[USD]") ? "USD" : null)
                .confidence(event.rawText().contains("[baja]") ? 0.5 : 0.99)
                .build();
    }
}
