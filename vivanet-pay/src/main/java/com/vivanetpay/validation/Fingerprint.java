package com.vivanetpay.validation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

/**
 * Huella SHA-256 de un evento (RN-005): fuente, conector, fecha (truncada al minuto), importe,
 * remitente, referencia y texto normalizado.
 */
public final class Fingerprint {
    private Fingerprint() {
    }

    public static String of(EventCandidate c) {
        String material = String.join("\u001F",
                c.source().name(),
                TextNormalizer.normalize(c.connectorId()),
                c.occurredAt().truncatedTo(ChronoUnit.MINUTES).toString(),
                c.amount() == null ? "" : Long.toString(c.amount()),
                TextNormalizer.normalize(c.sender()),
                TextNormalizer.normalize(c.reference()),
                TextNormalizer.normalize(c.rawText()));
        return sha256(material);
    }

    public static String sha256(String material) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
