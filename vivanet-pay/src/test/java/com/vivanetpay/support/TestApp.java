package com.vivanetpay.support;

import com.vivanetpay.app.AppConfig;
import com.vivanetpay.app.AppContext;
import com.vivanetpay.domain.EventSource;
import com.vivanetpay.domain.RawEvent;
import com.vivanetpay.voice.LoggingSpeechEngine;

import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** Fábrica de contextos de prueba con SQLite en archivo temporal. */
public final class TestApp {
    public static final Instant T0 = Instant.parse("2026-11-22T15:00:00Z"); // 10:00 en Bogotá

    public final MutableClock clock;
    public final LoggingSpeechEngine speech;
    public final AppContext ctx;

    private TestApp(Path dir, Map<String, String> props, Instant start, boolean realParsers) {
        this.clock = new MutableClock(start);
        this.speech = new LoggingSpeechEngine();
        Map<String, String> all = new HashMap<>();
        all.put("backup.onStart", "false");
        all.putAll(props);
        AppContext.Builder b = AppContext.builder(AppConfig.of(dir, all)).clock(clock).speechEngine(speech);
        if (!realParsers) {
            b.parser(new FakeNequiParser());
        }
        this.ctx = b.build();
    }

    public static TestApp create(Path dir) {
        return new TestApp(dir, Map.of(), T0, false);
    }

    public static TestApp create(Path dir, Map<String, String> props) {
        return new TestApp(dir, props, T0, false);
    }

    public static TestApp create(Path dir, Map<String, String> props, Instant start) {
        return new TestApp(dir, props, start, false);
    }

    /** Contexto con los parsers REALES por defecto (Nequi push/correo, Bancolombia) y la política por defecto. */
    public static TestApp createReal(Path dir, Map<String, String> props) {
        return new TestApp(dir, props, T0, true);
    }

    /** Evento Android Nequi con texto dado. */
    public RawEvent nequi(String externalId, String text) {
        return RawEvent.builder(EventSource.ANDROID_NEQUI, "android-principal")
                .externalEventId(externalId)
                .sourceTimestamp(clock.instant())
                .receivedAt(clock.instant())
                .subject("Nequi")
                .rawText(text)
                .build();
    }

    public RawEvent gmail(String messageId, String text) {
        return RawEvent.builder(EventSource.GMAIL, "gmail-principal")
                .externalEventId(messageId)
                .sourceTimestamp(clock.instant())
                .receivedAt(clock.instant())
                .subject("Recibiste dinero")
                .sender("notificaciones@nequi.com.co")
                .rawText(text)
                .build();
    }
}
