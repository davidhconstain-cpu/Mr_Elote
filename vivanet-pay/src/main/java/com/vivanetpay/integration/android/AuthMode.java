package com.vivanetpay.integration.android;

import java.util.Locale;

/**
 * Modo de autenticación del webhook ({@code connector.android.auth}). Por defecto {@link #TOKEN}; HMAC sigue
 * disponible por configuración ({@code connector.android.auth=hmac}).
 * <ul>
 *   <li>{@link #HMAC}: firma HMAC-SHA256 de timestamp + nonce + cuerpo. El secreto nunca viaja; cada solicitud es
 *       única (anti-replay por nonce y ventana de tiempo). Recomendado cuando el emisor puede calcular HMAC
 *       (Tasker/HTTP Shortcuts, puente propio, servicio intermedio futuro).</li>
 *   <li>{@link #TOKEN}: {@code Authorization: Bearer <secreto>}. Compatible con MacroDroid (no calcula HMAC).
 *       Trade-off: el secreto viaja en cada solicitud (por eso solo red local/HTTPS) y una solicitud capturada puede
 *       reenviarse; la idempotencia por {@code eventId} impide que el reenvío cree un segundo movimiento (CP-009),
 *       pero quien robe el token puede inyectar eventos falsos (R-02: los eventos Android nunca son prueba bancaria,
 *       DP-01). Mitigaciones: secreto de 256 bits, rotación, rate limit, bind solo loopback/LAN, TLS opcional.</li>
 * </ul>
 */
public enum AuthMode {
    HMAC, TOKEN;

    /** Modo por defecto (decisión del propietario): compatible con MacroDroid sin configuración extra. */
    public static final AuthMode DEFAULT = TOKEN;

    public static AuthMode parse(String s) {
        if (s == null || s.isBlank()) {
            return DEFAULT;
        }
        return switch (s.trim().toLowerCase(Locale.ROOT)) {
            case "hmac" -> HMAC;
            case "token", "bearer" -> TOKEN;
            default -> throw new IllegalArgumentException("connector.android.auth inválido (use hmac|token)");
        };
    }
}
