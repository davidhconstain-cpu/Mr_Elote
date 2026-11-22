package com.vivanetpay.voice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Selecciona el motor de voz según el sistema operativo. */
public final class SpeechEngines {
    private static final Logger log = LoggerFactory.getLogger(SpeechEngines.class);

    private SpeechEngines() {
    }

    /** {@link WindowsSpeechEngine} en Windows; {@link LoggingSpeechEngine} en cualquier otro sistema. */
    public static SpeechEngine create() {
        if (WindowsSpeechEngine.isWindows()) {
            return new WindowsSpeechEngine();
        }
        log.info("Sistema no Windows: la voz solo se registra en log");
        return new LoggingSpeechEngine();
    }
}
