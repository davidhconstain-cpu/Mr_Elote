package com.vivanetpay.integration.gmail;

import java.io.IOException;

/** Falta autorización o Google la rechazó (401, invalid_grant, permisos). El mensaje es apto para el usuario. */
public class GmailAuthException extends IOException {
    private static final long serialVersionUID = 1L;

    public GmailAuthException(String userMessage) {
        super(userMessage);
    }

    public GmailAuthException(String userMessage, Throwable cause) {
        super(userMessage, cause);
    }
}
