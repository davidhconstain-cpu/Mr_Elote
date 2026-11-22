package com.vivanetpay.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Utilidades de tiempo. Se persiste siempre en UTC; se presenta en America/Bogota (IDD §7.2, RN-009).
 */
public final class AppTime {
    /** Zona de presentación (Colombia, sin horario de verano). */
    public static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    public static final Locale LOCALE_CO = Locale.forLanguageTag("es-CO");
    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", LOCALE_CO);

    private AppTime() {
    }

    /** Fecha local de Colombia correspondiente a un instante (CP-015). */
    public static LocalDate localDate(Instant instant) {
        return instant.atZone(BOGOTA).toLocalDate();
    }

    /** Inicio (inclusive) del día local de Colombia, en UTC. */
    public static Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(BOGOTA).toInstant();
    }

    /** Fin (exclusivo) del día local de Colombia, en UTC. */
    public static Instant endOfDay(LocalDate date) {
        return date.plusDays(1).atStartOfDay(BOGOTA).toInstant();
    }

    public static ZonedDateTime toLocal(Instant instant) {
        return instant.atZone(BOGOTA);
    }

    /** Formato de presentación dd/MM/yyyy HH:mm:ss en hora de Colombia. */
    public static String format(Instant instant) {
        return instant == null ? "" : DISPLAY.format(instant.atZone(BOGOTA));
    }
}
