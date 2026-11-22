package com.vivanetpay.integration.gmail;

import java.io.IOException;

/** El historyId guardado ya no es válido (Gmail responde 404); hay que resincronizar. */
public class GmailHistoryExpiredException extends IOException {
    private static final long serialVersionUID = 1L;

    public GmailHistoryExpiredException(String message) {
        super(message);
    }
}
