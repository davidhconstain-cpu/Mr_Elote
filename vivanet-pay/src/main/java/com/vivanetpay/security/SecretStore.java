package com.vivanetpay.security;

import java.util.Optional;

/**
 * Almacén de secretos (tokens OAuth, secreto del webhook). En producción debe respaldarse en el almacén
 * seguro del sistema operativo (Windows Credential Manager/DPAPI, RNF-002).
 * <p>
 * Convención de claves: {@code "<componente>.<conector>.<nombre>"}, p. ej. {@code "gmail.gmail-principal.refresh_token"},
 * {@code "android.webhook.hmac_secret"}. Las claves pueden aparecer en logs; los valores NUNCA.
 * Las implementaciones deben ser seguras para hilos.
 */
public interface SecretStore {

    /** @return el secreto o vacío si no existe. */
    Optional<char[]> get(String key);

    /** Guarda o reemplaza un secreto. El llamador debe limpiar su copia del arreglo cuando termine. */
    void put(String key, char[] secret);

    /** Elimina el secreto (p. ej. al revocar Gmail). No falla si no existe. */
    void delete(String key);

    /** @return true si existe un secreto para la clave. */
    default boolean contains(String key) {
        return get(key).isPresent();
    }
}
