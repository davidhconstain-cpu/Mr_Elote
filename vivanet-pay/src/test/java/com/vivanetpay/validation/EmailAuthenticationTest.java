package com.vivanetpay.validation;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EmailAuthenticationTest {
    private static final Set<String> NEQUI = Set.of("nequi.com.co");

    private static boolean ok(String auth) {
        return EmailAuthentication.evaluate(auth == null ? Map.of() : Map.of("Authentication-Results", auth), NEQUI).passed();
    }

    @Test
    void dkimPassMasSpfOrDmarcAlineados() {
        assertThat(ok("mx.google.com; dkim=pass header.i=@nequi.com.co; spf=pass smtp.mailfrom=x@bounce.nequi.com.co")).isTrue();
        assertThat(ok("mx.google.com; dkim=pass header.d=mail.nequi.com.co; dmarc=pass header.from=nequi.com.co")).isTrue();
    }

    @Test
    void sinCabeceraOSoloDkimOFallosNoPasan() {
        assertThat(ok(null)).isFalse();
        assertThat(ok("mx.google.com; dkim=pass header.i=@nequi.com.co")).isFalse();
        assertThat(ok("mx.google.com; dkim=fail header.i=@nequi.com.co; spf=pass smtp.mailfrom=x@nequi.com.co")).isFalse();
        assertThat(ok("mx.google.com; dkim=pass header.i=@nequi.com.co; spf=pass smtp.mailfrom=x@nequi.com.co; dmarc=fail header.from=nequi.com.co")).isFalse();
        assertThat(ok("mx.google.com; dkim=pass header.i=@otro.com; spf=pass smtp.mailfrom=x@otro.com")).isFalse();
        assertThat(ok("evil.example; dkim=pass header.i=@nequi.com.co; spf=pass smtp.mailfrom=x@nequi.com.co")).isFalse();
    }

    @Test
    void cabeceraInyectadaPorElRemitenteNoSumaALaDeGoogle() {
        String forged = "evil.example; dkim=pass header.i=@nequi.com.co; spf=pass smtp.mailfrom=x@nequi.com.co";
        String real = "mx.google.com; dkim=fail header.i=@nequi.com.co; spf=fail smtp.mailfrom=x@nequi.com.co";
        assertThat(ok(forged + "\n" + real)).isFalse();
    }

    @Test
    void arcSeUsaSoloSiFaltaAuthenticationResults() {
        var arc = Map.of("ARC-Authentication-Results", "i=1; mx.google.com; dkim=pass header.i=@nequi.com.co; spf=pass smtp.mailfrom=x@nequi.com.co");
        assertThat(EmailAuthentication.evaluate(arc, NEQUI).passed()).isTrue();
    }
}
