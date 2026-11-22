package com.vivanetpay.parsing;

import com.vivanetpay.domain.RawEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registro de parsers. Evalúa los parsers que declaran {@code supports()} en orden de prioridad
 * descendente y devuelve el primer resultado reconocido. Si ninguno reconoce el evento, devuelve el
 * primer motivo de no reconocimiento (o el del parser de respaldo).
 * <p>
 * Seguro para hilos. Un parser que lanza excepción se trata como "no reconocido" y se registra en log.
 */
public final class ParserRegistry {
    private static final Logger log = LoggerFactory.getLogger(ParserRegistry.class);

    private final CopyOnWriteArrayList<PaymentParser> parsers = new CopyOnWriteArrayList<>();
    private final PaymentParser fallback = new UnrecognizedFallbackParser();

    public ParserRegistry() {
    }

    public ParserRegistry(List<PaymentParser> initial) {
        initial.forEach(this::register);
    }

    /** Registra un parser. Ids duplicados se rechazan. */
    public synchronized void register(PaymentParser parser) {
        Objects.requireNonNull(parser, "parser");
        for (PaymentParser p : parsers) {
            if (p.id().equals(parser.id())) {
                throw new IllegalArgumentException("Parser ya registrado: " + parser.id());
            }
        }
        List<PaymentParser> sorted = new ArrayList<>(parsers);
        sorted.add(parser);
        sorted.sort(Comparator.comparingInt(PaymentParser::priority).reversed());
        parsers.clear();
        parsers.addAll(sorted);
    }

    public List<PaymentParser> parsers() {
        return List.copyOf(parsers);
    }

    /** Analiza el evento. Nunca devuelve null ni lanza por errores de parser. */
    public ParseResult parse(RawEvent event) {
        ParseResult firstUnrecognized = null;
        for (PaymentParser parser : parsers) {
            boolean supported;
            try {
                supported = parser.supports(event);
            } catch (RuntimeException | StackOverflowError e) { // un texto hostil no debe envenenar la cola de ingesta
                log.warn("Parser {} falló en supports(): {}", parser.id(), e.getClass().getSimpleName());
                continue;
            }
            if (!supported) {
                continue;
            }
            ParseResult result;
            try {
                result = parser.parse(event);
            } catch (RuntimeException | StackOverflowError e) {
                log.warn("Parser {} lanzó excepción; se trata como no reconocido: {}", parser.id(), e.getClass().getSimpleName());
                result = ParseResult.notRecognized(parser.id(), "Error interno del analizador " + parser.id());
            }
            if (result == null) {
                result = ParseResult.notRecognized(parser.id(), "El analizador no devolvió resultado");
            }
            if (result.recognized()) {
                return result;
            }
            if (firstUnrecognized == null) {
                firstUnrecognized = result;
            }
        }
        return firstUnrecognized != null ? firstUnrecognized : fallback.parse(event);
    }
}
