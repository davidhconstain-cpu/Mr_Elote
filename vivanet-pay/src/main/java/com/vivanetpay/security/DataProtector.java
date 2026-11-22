package com.vivanetpay.security;

import java.io.IOException;

/**
 * Cifrado local ligado al usuario del sistema operativo (p. ej. DPAPI {@code CurrentUser}).
 * {@code entropy} es un valor adicional no secreto que liga el blob a su propósito (clave del secreto).
 */
public interface DataProtector {

    byte[] protect(byte[] plaintext, byte[] entropy) throws IOException;

    byte[] unprotect(byte[] blob, byte[] entropy) throws IOException;
}
