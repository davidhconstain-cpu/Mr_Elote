package com.vivanetpay.integration.gmail;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Ajustes no secretos del conector (claves {@code connector.gmail.*} en config.properties; aquí llegan sin el
 * prefijo). También se acepta el prefijo {@code gmail.}.
 *
 * @param clientSecretsPath archivo client_secret.json de Google Cloud (fuera del repositorio)
 * @param query             consulta de Gmail para la sincronización inicial/resincronización
 * @param senders           dominios/remitentes permitidos; filtro local de la sincronización incremental
 * @param pollInterval      intervalo de sondeo
 * @param initialMax        máximo de mensajes en una sincronización inicial
 * @param backoffBase       base del backoff exponencial
 * @param backoffMax        tope del backoff
 */
public record GmailSettings(Path clientSecretsPath, String query, List<String> senders, Duration pollInterval,
                            int initialMax, Duration backoffBase, Duration backoffMax) {

    public static final List<String> DEFAULT_SENDERS = List.of("nequi.com.co", "bancolombia.com.co");

    public static GmailSettings from(Map<String, String> s, Path dataDir) {
        Path secrets = optional(s, "clientSecretsPath").map(Path::of)
                .orElse(dataDir.resolve("gmail").resolve("client_secret.json"));
        List<String> senders = new ArrayList<>();
        String sendersRaw = optional(s, "senders").orElse(null);
        if (sendersRaw == null) {
            senders.addAll(DEFAULT_SENDERS);
        } else {
            for (String p : sendersRaw.split("[,;\\s]+")) {
                if (!p.isBlank()) {
                    senders.add(p.trim().toLowerCase(java.util.Locale.ROOT));
                }
            }
        }
        String query = optional(s, "query").orElse("from:(" + String.join(" OR ", senders) + ") newer_than:2d");
        return new GmailSettings(secrets, query, List.copyOf(senders),
                Duration.ofSeconds(Math.max(5, num(s, "pollSeconds", 60))), (int) num(s, "initialMax", 50),
                Duration.ofSeconds(Math.max(1, num(s, "backoffBaseSeconds", 5))),
                Duration.ofSeconds(Math.max(1, num(s, "backoffMaxSeconds", 900))));
    }

    private static java.util.Optional<String> optional(Map<String, String> s, String key) {
        String v = s.get(key);
        if (v == null) {
            v = s.get("gmail." + key);
        }
        return v == null || v.isBlank() ? java.util.Optional.empty() : java.util.Optional.of(v.trim());
    }

    private static long num(Map<String, String> s, String key, long def) {
        return optional(s, key).map(v -> {
            try {
                return Long.parseLong(v);
            } catch (NumberFormatException e) {
                return def;
            }
        }).orElse(def);
    }

    /** true si el remitente (valor de la cabecera From) pertenece a un dominio/dirección permitida. */
    boolean senderAllowed(String fromHeader) {
        if (senders.isEmpty()) {
            return true;
        }
        if (fromHeader == null) {
            return false;
        }
        String f = fromHeader.toLowerCase(java.util.Locale.ROOT);
        int lt = f.lastIndexOf('<');
        int gt = f.lastIndexOf('>');
        String addr = lt >= 0 && gt > lt ? f.substring(lt + 1, gt) : f;
        addr = addr.trim();
        int at = addr.lastIndexOf('@');
        String domain = at >= 0 ? addr.substring(at + 1) : addr;
        for (String s : senders) {
            if (s.contains("@") ? addr.equals(s) : (domain.equals(s) || domain.endsWith("." + s))) {
                return true;
            }
        }
        return false;
    }
}
