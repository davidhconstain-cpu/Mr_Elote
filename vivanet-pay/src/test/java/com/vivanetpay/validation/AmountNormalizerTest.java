package com.vivanetpay.validation;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class AmountNormalizerTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "$50.000|50000",
            "COP $50.000|50000",
            "$1.250.000|1250000",
            "$ 1.250.000,00|1250000",
            "50000|50000",
            "50.000,00|50000",
            "1,250,000|1250000",
            "$50,000.00|50000",
            "125.000 pesos|125000",
            "$999|999",
            "1.000|1000",
            "$ 7,5|-1",
    })
    void normalizaAEnterosEnPesos(String text, long expected) {
        if (expected < 0) {
            assertThat(AmountNormalizer.parsePesos(text)).isEmpty();
        } else {
            assertThat(AmountNormalizer.parsePesos(text)).contains(expected);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "$", "-50.000", "1.25.000", "50.000,50", "12.5", "1,2,3", "$.500", "1.000.00"})
    void rechazaFormatosAmbiguosOConCentavos(String text) {
        assertThat(AmountNormalizer.parsePesos(text)).isEmpty();
    }

    @org.junit.jupiter.api.Test
    void nuloEsVacio() {
        assertThat(AmountNormalizer.parsePesos(null)).isEmpty();
    }
}
