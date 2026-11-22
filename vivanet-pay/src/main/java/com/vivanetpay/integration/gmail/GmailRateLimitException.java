package com.vivanetpay.integration.gmail;

import java.io.IOException;

/** Google limitó las consultas (429 o 403 rateLimitExceeded). Se reintenta con backoff. */
public class GmailRateLimitException extends IOException {
    private static final long serialVersionUID = 1L;

    public GmailRateLimitException(String message) {
        super(message);
    }
}
