package com.vivanetpay.integration.android;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Caché anti-replay de nonces, acotada en memoria con expiración (TTL = 2 x ventana de vigencia del timestamp:
 * un nonce expirado ya no puede reutilizarse porque su timestamp quedó fuera de la ventana).
 * <p>
 * Si se llena con nonces vigentes NO se expulsan (eso reabriría el replay): se rechaza con {@link Result#FULL}.
 * Tras reiniciar la app la caché se vacía; el replay dentro de la ventana sigue sin crear movimientos porque el
 * núcleo deduplica por {@code eventId} (CP-009).
 */
final class NonceCache {
    enum Result { ACCEPTED, REPLAY, FULL }

    private final int maxEntries;
    private final Duration ttl;
    private final Map<String, Instant> entries = new LinkedHashMap<>();

    NonceCache(int maxEntries, Duration ttl) {
        this.maxEntries = maxEntries;
        this.ttl = ttl;
    }

    synchronized Result register(String nonce, Instant now) {
        purge(now);
        Instant exp = entries.get(nonce);
        if (exp != null && exp.isAfter(now)) {
            return Result.REPLAY;
        }
        if (entries.size() >= maxEntries) {
            return Result.FULL;
        }
        entries.put(nonce, now.plus(ttl));
        return Result.ACCEPTED;
    }

    synchronized int size() {
        return entries.size();
    }

    private void purge(Instant now) {
        Iterator<Map.Entry<String, Instant>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            if (!it.next().getValue().isAfter(now)) {
                it.remove();
            }
        }
    }
}
