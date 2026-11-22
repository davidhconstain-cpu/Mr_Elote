package com.vivanetpay.observability;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/** Conversor Logback ({@code %rmsg}) que aplica {@link Redactor} al mensaje formateado. */
public class RedactingMessageConverter extends MessageConverter {
    @Override
    public String convert(ILoggingEvent event) {
        return Redactor.redact(super.convert(event));
    }
}
