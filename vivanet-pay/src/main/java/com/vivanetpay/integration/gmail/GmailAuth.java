package com.vivanetpay.integration.gmail;

import java.io.IOException;

/** Autorización OAuth de Gmail (separada del cliente de API para poder probar sin red). */
public interface GmailAuth {

    /** @return true si hay credenciales guardadas localmente. */
    boolean hasCredentials();

    /** Flujo interactivo (navegador + loopback + PKCE). Bloqueante. */
    GmailClient authorize() throws IOException;

    /** Cliente con las credenciales guardadas; {@link GmailAuthException} si no hay. */
    GmailClient openClient() throws IOException;

    /** Revoca en Google y borra los tokens locales (los borra aunque falle la llamada remota, que se informa). */
    void revoke() throws IOException;
}
