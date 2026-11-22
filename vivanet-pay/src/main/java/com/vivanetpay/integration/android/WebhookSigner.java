package com.vivanetpay.integration.android;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

/**
 * Firma del modo HMAC. Cadena firmada (bytes):
 * <pre>"v1\n" + X-Vivanet-Timestamp + "\n" + X-Vivanet-Nonce + "\n" + cuerpo-exacto</pre>
 * Firma: {@code X-Vivanet-Signature: v1=<hex minúsculas de HMAC-SHA256(secreto UTF-8, cadena)>} (también se
 * acepta el hex sin prefijo). Timestamp = segundos epoch UTC; nonce = 16..128 caracteres {@code [A-Za-z0-9_-]}.
 */
public final class WebhookSigner {
    public static final String HEADER_TIMESTAMP = "X-Vivanet-Timestamp";
    public static final String HEADER_NONCE = "X-Vivanet-Nonce";
    public static final String HEADER_SIGNATURE = "X-Vivanet-Signature";
    public static final String VERSION_PREFIX = "v1=";

    private WebhookSigner() {
    }

    /** HMAC-SHA256 crudo de la cadena canónica. */
    public static byte[] mac(byte[] secret, String timestamp, String nonce, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            mac.update(("v1\n" + timestamp + "\n" + nonce + "\n").getBytes(StandardCharsets.UTF_8));
            mac.update(body);
            return mac.doFinal();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 no disponible", e);
        }
    }

    /** Valor completo de la cabecera {@value #HEADER_SIGNATURE}. */
    public static String signatureHeader(byte[] secret, String timestamp, String nonce, byte[] body) {
        return VERSION_PREFIX + HexFormat.of().formatHex(mac(secret, timestamp, nonce, body));
    }
}
