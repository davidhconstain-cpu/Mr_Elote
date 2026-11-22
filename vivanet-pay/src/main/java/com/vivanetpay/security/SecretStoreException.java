package com.vivanetpay.security;

/** Fallo de E/S o de cifrado en un {@link SecretStore}. El mensaje nunca contiene el secreto. */
public class SecretStoreException extends RuntimeException {
    public SecretStoreException(String message, Throwable cause) {
        super(message, cause);
    }

    public SecretStoreException(String message) {
        super(message);
    }
}
