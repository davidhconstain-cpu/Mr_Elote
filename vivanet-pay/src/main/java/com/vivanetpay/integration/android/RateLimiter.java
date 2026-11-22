package com.vivanetpay.integration.android;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Token bucket por IP de origen. Capacidad {@code burst}, recarga {@code perMinute} tokens/minuto. Los fallos de
 * autenticación consumen tokens extra ({@link #penalize}) para frenar fuerza bruta. Memoria acotada.
 */
final class RateLimiter {
    private static final int MAX_TRACKED = 4_096;

    private final int burst;
    private final double tokensPerNano;
    private final LongSupplier nanoTime;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    RateLimiter(int burst, int perMinute, LongSupplier nanoTime) {
        this.burst = burst;
        this.tokensPerNano = perMinute / 60_000_000_000.0;
        this.nanoTime = nanoTime;
    }

    /** @return true si la solicitud puede atenderse. */
    boolean tryAcquire(String ip) {
        if (buckets.size() > MAX_TRACKED) {
            evictFull();
        }
        return buckets.computeIfAbsent(ip, k -> new Bucket(burst, nanoTime.getAsLong())).take(1);
    }

    void penalize(String ip, int tokens) {
        Bucket b = buckets.get(ip);
        if (b != null) {
            b.take(tokens);
        }
    }

    /** Segundos aproximados hasta disponer de un token (cabecera Retry-After). */
    long retryAfterSeconds() {
        return Math.max(1, (long) Math.ceil(1.0 / (tokensPerNano * 1_000_000_000.0)));
    }

    private void evictFull() {
        long now = nanoTime.getAsLong();
        buckets.entrySet().removeIf(e -> e.getValue().isFull(now));
        if (buckets.size() > MAX_TRACKED) {
            buckets.clear(); // degradación segura ante muchas IPs distintas: solo se pierde el historial de tasa
        }
    }

    private final class Bucket {
        private double tokens;
        private long last;

        Bucket(double tokens, long now) {
            this.tokens = tokens;
            this.last = now;
        }

        synchronized boolean take(int n) {
            refill(nanoTime.getAsLong());
            if (tokens >= n) {
                tokens -= n;
                return true;
            }
            tokens = Math.max(tokens - n, -burst); // la penalización acumula deuda acotada
            return false;
        }

        synchronized boolean isFull(long now) {
            refill(now);
            return tokens >= burst;
        }

        private void refill(long now) {
            long elapsed = Math.max(0, now - last);
            last = now;
            tokens = Math.min(burst, tokens + elapsed * tokensPerNano);
        }
    }
}
