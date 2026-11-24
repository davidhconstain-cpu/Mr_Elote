package com.vivanetpay.integration.android;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Decisión del propietario: el modo por defecto del webhook Android es TOKEN; HMAC sigue disponible. */
class AuthModeDefaultTest {
    @Test
    void sinConfigurarElModoEsToken() {
        assertThat(AuthMode.parse(null)).isEqualTo(AuthMode.TOKEN);
        assertThat(AuthMode.parse("  ")).isEqualTo(AuthMode.TOKEN);
        assertThat(AndroidWebhookConfig.defaults().authMode()).isEqualTo(AuthMode.TOKEN);
    }

    @Test
    void hmacYTokenSePuedenElegirExplicitamente() {
        assertThat(AndroidWebhookConfig.from(Map.of("auth", "hmac")).authMode()).isEqualTo(AuthMode.HMAC);
        assertThat(AndroidWebhookConfig.from(Map.of("auth", "HMAC")).authMode()).isEqualTo(AuthMode.HMAC);
        assertThat(AndroidWebhookConfig.from(Map.of("auth", "token")).authMode()).isEqualTo(AuthMode.TOKEN);
        assertThat(AndroidWebhookConfig.from(Map.of("auth", "bearer")).authMode()).isEqualTo(AuthMode.TOKEN);
        assertThatThrownBy(() -> AuthMode.parse("otro")).isInstanceOf(IllegalArgumentException.class);
    }
}
