package com.vivanetpay.integration;

import java.util.Optional;

/**
 * Conector que requiere autorización del usuario (OAuth) y que puede revocarse desde la aplicación (RF-021, IDD 8.3).
 * <p>
 * {@link #authorize()} y {@link #revoke()} son BLOQUEANTES (abren el navegador / llaman a la red): nunca
 * invocarlos desde el hilo de la interfaz. Los fallos se informan con {@link IllegalStateException} cuyo mensaje
 * está en español, es apto para mostrar al usuario y no contiene secretos.
 */
public interface AuthorizableConnector extends Connector {

    /** Abre el flujo de autorización en el navegador y espera a que termine. */
    void authorize();

    /** Revoca el acceso (remoto si es posible), borra credenciales locales y deja REQUIERE_AUTORIZACION. */
    void revoke();

    /** @return true si hay credenciales locales guardadas (no garantiza que Google las siga aceptando). */
    boolean isAuthorized();

    /** @return cuenta autorizada (p. ej. correo), si se conoce. */
    default Optional<String> authorizedAccount() {
        return Optional.empty();
    }
}
