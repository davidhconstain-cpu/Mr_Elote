package com.vivanetpay.observability;

import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.IThrowableProxy;

/** Conversor Logback ({@code %rex}) que redacta los mensajes de excepción y trazas. */
public class RedactingThrowableConverter extends ThrowableProxyConverter {
    @Override
    protected String throwableProxyToString(IThrowableProxy tp) {
        return Redactor.redact(super.throwableProxyToString(tp));
    }
}
