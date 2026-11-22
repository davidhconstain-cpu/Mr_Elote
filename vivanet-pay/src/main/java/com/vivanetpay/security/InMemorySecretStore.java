package com.vivanetpay.security;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Implementación en memoria SOLO para pruebas y modo sin UI. No persiste nada. */
public final class InMemorySecretStore implements SecretStore {
    private final Map<String, char[]> secrets = new ConcurrentHashMap<>();

    @Override
    public Optional<char[]> get(String key) {
        char[] v = secrets.get(Objects.requireNonNull(key, "key"));
        return v == null ? Optional.empty() : Optional.of(v.clone());
    }

    @Override
    public void put(String key, char[] secret) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(secret, "secret");
        char[] old = secrets.put(key, secret.clone());
        if (old != null) {
            Arrays.fill(old, '\0');
        }
    }

    @Override
    public void delete(String key) {
        char[] old = secrets.remove(Objects.requireNonNull(key, "key"));
        if (old != null) {
            Arrays.fill(old, '\0');
        }
    }

    @Override
    public String toString() {
        return "InMemorySecretStore[" + secrets.size() + " claves]";
    }
}
