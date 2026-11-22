package com.vivanetpay.integration.android;

import com.vivanetpay.security.SecretBytes;
import com.vivanetpay.security.SecretStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/**
 * Secreto compartido del webhook (IDD §8.1, §8.3): 32 bytes de {@link SecureRandom} en Base64URL (43 caracteres),
 * guardado SOLO en {@link SecretStore}, nunca en logs ni en config.
 * <ul>
 *   <li>Claves: {@code android.<id>.webhook_secret}, {@code ...webhook_secret_previous},
 *       {@code ...webhook_secret_previous_expires} (epoch ms; no secreto pero se guarda junto).</li>
 *   <li>{@link #rotate()}: genera uno nuevo; el anterior sigue válido durante el periodo de gracia para dar tiempo a
 *       actualizar MacroDroid.</li>
 *   <li>{@link #revokeAndRegenerate()}: ante sospecha de exposición; invalida el anterior de inmediato.</li>
 * </ul>
 * Los valores se cachean en memoria (DPAPI vía PowerShell es lento) como {@code byte[]}.
 */
public final class WebhookSecretManager {
    private static final Logger log = LoggerFactory.getLogger(WebhookSecretManager.class);
    public static final int SECRET_BYTES = 32;

    private final SecretStore store;
    private final Clock clock;
    private final Duration grace;
    private final String currentKey;
    private final String previousKey;
    private final String previousExpiresKey;
    private final SecureRandom random = new SecureRandom();

    private byte[] current;
    private byte[] previous;
    private Instant previousExpires;

    public WebhookSecretManager(SecretStore store, String connectorId, Clock clock, Duration grace) {
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.grace = Objects.requireNonNull(grace, "grace");
        String base = "android." + connectorId + ".";
        this.currentKey = base + "webhook_secret";
        this.previousKey = base + "webhook_secret_previous";
        this.previousExpiresKey = base + "webhook_secret_previous_expires";
    }

    public String currentKeyName() {
        return currentKey;
    }

    /** Carga el secreto o lo genera si no existe. Idempotente. */
    public synchronized void ensureSecret() {
        if (current != null) {
            return;
        }
        var existing = store.get(currentKey);
        if (existing.isPresent()) {
            char[] c = existing.get();
            current = SecretBytes.toUtf8(c);
            SecretBytes.wipe(c);
        } else {
            char[] fresh = generate();
            store.put(currentKey, fresh);
            current = SecretBytes.toUtf8(fresh);
            SecretBytes.wipe(fresh);
            log.info("Secreto del webhook Android generado ({}).", currentKey);
        }
        var prev = store.get(previousKey);
        var exp = store.get(previousExpiresKey);
        if (prev.isPresent() && exp.isPresent()) {
            try {
                previousExpires = Instant.ofEpochMilli(Long.parseLong(new String(exp.get())));
                previous = SecretBytes.toUtf8(prev.get());
            } catch (NumberFormatException e) {
                clearPrevious();
            }
            SecretBytes.wipe(prev.get());
        }
    }

    /**
     * Copia del secreto vigente para mostrarlo al usuario (configurar MacroDroid). El llamador debe borrarla con
     * {@link SecretBytes#wipe(char[])}. Nunca loguear.
     */
    public synchronized char[] revealCurrent() {
        ensureSecret();
        return SecretBytes.fromUtf8(current);
    }

    /**
     * Rota el secreto. El anterior se acepta hasta {@code ahora + gracia}.
     *
     * @return el nuevo secreto (borrar tras mostrarlo)
     */
    public synchronized char[] rotate() {
        ensureSecret();
        char[] fresh = generate();
        char[] old = SecretBytes.fromUtf8(current);
        Instant expires = clock.instant().plus(grace);
        store.put(previousKey, old);
        store.put(previousExpiresKey, Long.toString(expires.toEpochMilli()).toCharArray());
        store.put(currentKey, fresh);
        SecretBytes.wipe(old);
        SecretBytes.wipe(previous);
        previous = current;
        previousExpires = expires;
        current = SecretBytes.toUtf8(fresh);
        log.info("Secreto del webhook Android rotado; el anterior es válido hasta {}.", expires);
        return fresh;
    }

    /** Revoca el secreto actual y el anterior sin periodo de gracia y genera uno nuevo. */
    public synchronized char[] revokeAndRegenerate() {
        ensureSecret();
        char[] fresh = generate();
        store.put(currentKey, fresh);
        SecretBytes.wipe(current);
        current = SecretBytes.toUtf8(fresh);
        clearPrevious();
        log.warn("Secreto del webhook Android revocado y regenerado; las solicitudes con el anterior se rechazarán.");
        return fresh;
    }

    /** Secretos aceptados ahora (vigente y, si no expiró, el anterior). Copias: el llamador debe borrarlas. */
    synchronized List<byte[]> acceptedSecrets() {
        ensureSecret();
        List<byte[]> out = new ArrayList<>(2);
        out.add(current.clone());
        if (previous != null) {
            if (clock.instant().isBefore(previousExpires)) {
                out.add(previous.clone());
            } else {
                clearPrevious();
            }
        }
        return out;
    }

    /** @return instante hasta el que se acepta el secreto anterior, o null si no hay. */
    public synchronized Instant previousValidUntil() {
        return previous == null ? null : previousExpires;
    }

    private void clearPrevious() {
        SecretBytes.wipe(previous);
        previous = null;
        previousExpires = null;
        store.delete(previousKey);
        store.delete(previousExpiresKey);
    }

    private char[] generate() {
        byte[] raw = new byte[SECRET_BYTES];
        random.nextBytes(raw);
        byte[] enc = Base64.getUrlEncoder().withoutPadding().encode(raw);
        char[] out = new char[enc.length];
        for (int i = 0; i < enc.length; i++) {
            out[i] = (char) enc[i]; // Base64URL es ASCII; se evita crear un String inmutable con el secreto
        }
        SecretBytes.wipe(raw);
        SecretBytes.wipe(enc);
        return out;
    }
}
