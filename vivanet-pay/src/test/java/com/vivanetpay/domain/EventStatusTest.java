package com.vivanetpay.domain;

import org.junit.jupiter.api.Test;

import static com.vivanetpay.domain.EventStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventStatusTest {

    @Test
    void tieneLosOchoEstadosDeRN002() {
        assertThat(EventStatus.values()).containsExactly(RECIBIDO, PARSEADO, PENDIENTE_REVISION, VALIDADO_POR_FUENTE,
                ANUNCIADO, DUPLICADO, RECHAZADO, ERROR);
    }

    @Test
    void transicionesPermitidas() {
        assertThat(RECIBIDO.canTransitionTo(PARSEADO)).isTrue();
        assertThat(RECIBIDO.canTransitionTo(DUPLICADO)).isTrue();
        assertThat(PARSEADO.canTransitionTo(PENDIENTE_REVISION)).isTrue();
        assertThat(PENDIENTE_REVISION.canTransitionTo(VALIDADO_POR_FUENTE)).isTrue();
        assertThat(VALIDADO_POR_FUENTE.canTransitionTo(ANUNCIADO)).isTrue();
        assertThat(ANUNCIADO.canTransitionTo(RECHAZADO)).isTrue();
        assertThat(ERROR.canTransitionTo(RECIBIDO)).isTrue();
    }

    @Test
    void transicionesProhibidas() {
        assertThat(PENDIENTE_REVISION.canTransitionTo(ANUNCIADO)).as("no confirmado no puede anunciarse como confirmado").isFalse();
        assertThat(PARSEADO.canTransitionTo(ANUNCIADO)).isFalse();
        assertThat(RECIBIDO.canTransitionTo(ANUNCIADO)).isFalse();
        assertThat(DUPLICADO.canTransitionTo(VALIDADO_POR_FUENTE)).isFalse();
        assertThat(RECHAZADO.canTransitionTo(VALIDADO_POR_FUENTE)).isFalse();
        assertThat(PARSEADO.canTransitionTo(PARSEADO)).isFalse();
        assertThat(PARSEADO.canTransitionTo(null)).isFalse();
        assertThatThrownBy(() -> DUPLICADO.requireTransitionTo(PARSEADO)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void terminalesYConfirmados() {
        assertThat(DUPLICADO.isTerminal()).isTrue();
        assertThat(RECHAZADO.isTerminal()).isTrue();
        assertThat(PARSEADO.isTerminal()).isFalse();
        for (EventStatus s : values()) {
            assertThat(s.isConfirmed()).isEqualTo(s == VALIDADO_POR_FUENTE || s == ANUNCIADO);
        }
    }

    @Test
    void soloEstadosConfirmadosLleganAAnunciado() {
        for (EventStatus s : values()) {
            if (s.canTransitionTo(ANUNCIADO)) {
                assertThat(s).isEqualTo(VALIDADO_POR_FUENTE);
            }
        }
    }
}
