package com.vivanetpay.integration.android;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Configuración NO secreta del receptor (claves {@code connector.android.<clave>} en config.properties; el prefijo
 * llega eliminado en {@code ConnectorDependencies.settings()}).
 *
 * <pre>
 * bindAddress=127.0.0.1             lista separada por comas; para recibir del teléfono añadir la IP LAN del PC,
 *                                   p. ej. 127.0.0.1,192.168.1.20. 0.0.0.0/:: solo con allowWildcardBind=true.
 * port=47390                        0 = efímero (pruebas)
 * auth=hmac                         hmac | token (MacroDroid)
 * tls.keystore=                     ruta a PKCS12; contraseña en SecretStore "android.&lt;id&gt;.tls_keystore_password"
 * maxSkewSeconds=300                ventana de vigencia del timestamp (modo hmac)
 * maxBodyBytes=16384                tope real del cuerpo (máx. 65536)
 * rateLimit.burst=20                token bucket por IP: capacidad
 * rateLimit.perMinute=60            token bucket por IP: recarga
 * nonceCacheSize=10000              nonces recordados (memoria acotada, expiran tras 2x ventana)
 * rotationGraceMinutes=10           validez del secreto anterior tras rotar
 * ingestTimeoutMs=5000              espera máxima por el núcleo antes de responder 202 "processing"
 * allowWildcardBind=false
 * </pre>
 */
public record AndroidWebhookConfig(List<String> bindAddresses, int port, AuthMode authMode, Path tlsKeystore,
                                   Duration maxSkew, int maxBodyBytes, int rateBurst, int ratePerMinute,
                                   int nonceCacheSize, Duration rotationGrace, Duration ingestTimeout,
                                   boolean allowWildcardBind) {
    public static final int DEFAULT_PORT = 47390;
    public static final int HARD_MAX_BODY = 65_536;
    public static final String EVENTS_PATH = "/v1/events";
    public static final String HEALTH_PATH = "/health";

    public AndroidWebhookConfig {
        bindAddresses = List.copyOf(bindAddresses);
        if (bindAddresses.isEmpty()) {
            throw new IllegalArgumentException("bindAddress vacío");
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("port inválido");
        }
        if (maxBodyBytes < 512 || maxBodyBytes > HARD_MAX_BODY) {
            throw new IllegalArgumentException("maxBodyBytes debe estar entre 512 y " + HARD_MAX_BODY);
        }
        if (rateBurst < 1 || ratePerMinute < 1) {
            throw new IllegalArgumentException("rateLimit inválido");
        }
        if (nonceCacheSize < 100) {
            throw new IllegalArgumentException("nonceCacheSize mínimo 100");
        }
        if (maxSkew.isNegative() || maxSkew.isZero() || maxSkew.compareTo(Duration.ofMinutes(30)) > 0) {
            throw new IllegalArgumentException("maxSkewSeconds debe estar entre 1 y 1800");
        }
    }

    public static AndroidWebhookConfig defaults() {
        return from(Map.of());
    }

    public static AndroidWebhookConfig from(Map<String, String> s) {
        List<String> binds = new ArrayList<>();
        for (String a : s.getOrDefault("bindAddress", "127.0.0.1").split(",")) {
            if (!a.isBlank()) {
                binds.add(a.trim());
            }
        }
        String ks = s.get("tls.keystore");
        return new AndroidWebhookConfig(binds,
                integer(s, "port", DEFAULT_PORT),
                AuthMode.parse(s.get("auth")),
                ks == null || ks.isBlank() ? null : Path.of(ks.trim()),
                Duration.ofSeconds(integer(s, "maxSkewSeconds", 300)),
                integer(s, "maxBodyBytes", 16_384),
                integer(s, "rateLimit.burst", 20),
                integer(s, "rateLimit.perMinute", 60),
                integer(s, "nonceCacheSize", 10_000),
                Duration.ofMinutes(integer(s, "rotationGraceMinutes", 10)),
                Duration.ofMillis(integer(s, "ingestTimeoutMs", 5_000)),
                Boolean.parseBoolean(s.getOrDefault("allowWildcardBind", "false").trim()));
    }

    public boolean tlsEnabled() {
        return tlsKeystore != null;
    }

    private static int integer(Map<String, String> s, String key, int def) {
        String v = s.get(key);
        if (v == null || v.isBlank()) {
            return def;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("connector.android." + key + " no es un entero");
        }
    }
}
