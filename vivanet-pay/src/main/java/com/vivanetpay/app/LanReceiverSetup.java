package com.vivanetpay.app;

import com.vivanetpay.domain.ConnectorStatus;
import com.vivanetpay.domain.ConnectorType;
import com.vivanetpay.integration.Connector;
import com.vivanetpay.integration.android.AndroidWebhookConfig;
import com.vivanetpay.integration.android.AndroidWebhookConnector;
import com.vivanetpay.integration.android.LanAddresses;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuración "Recibir desde mi teléfono en esta Wi-Fi" (DP-03/DP-04): persiste
 * {@code connector.android.bindAddress=127.0.0.1,<ip>} y construye la URL que se pega en MacroDroid.
 * Pura salvo la escritura atómica de config.properties; el reinicio del conector lo hace la capa de aplicación.
 */
public final class LanReceiverSetup {
    public static final String BIND_KEY = "connector.android.bindAddress";
    public static final String LOOPBACK = "127.0.0.1";

    private LanReceiverSetup() {
    }

    /**
     * Aplica la IP elegida: guarda la configuración, reinicia el conector Android y comprueba que quedó escuchando.
     * Si no arranca, restaura la configuración anterior y vuelve a levantar el receptor como estaba.
     *
     * @return la URL para MacroDroid
     * @throws IllegalStateException si el receptor no pudo escuchar en esa IP (configuración restaurada)
     */
    public static String apply(AppContext ctx, String lanIp) {
        return applyUnchecked(ctx, LanAddresses.requirePrivate(lanIp));
    }

    /** Igual que {@link #apply} pero SIN exigir IP privada (solo para pruebas con 127.0.0.2). */
    static String applyUnchecked(AppContext ctx, String lanIp) {
        return applyUnchecked(ctx, lanIp, null);
    }

    static String applyUnchecked(AppContext ctx, String lanIp, com.vivanetpay.integration.ConnectorProvider provider) {
        String previous = ctx.config().get(BIND_KEY, null);
        ctx.config().set(BIND_KEY, LOOPBACK + "," + lanIp);
        Connector c = Bootstrap.restartConnector(ctx, ConnectorType.ANDROID_WEBHOOK, provider);
        if (c == null || c.state().status() == ConnectorStatus.ERROR) {
            String detail = c == null ? "el conector Android no está habilitado" : c.state().detail();
            restore(ctx.config(), previous);
            Bootstrap.restartConnector(ctx, ConnectorType.ANDROID_WEBHOOK, provider);
            throw new IllegalStateException("No se pudo recibir en " + lanIp + ": " + detail);
        }
        boolean tls = c instanceof AndroidWebhookConnector a && a.tlsEnabled();
        int port = ctx.config().getInt("connector.android.port", AndroidWebhookConfig.DEFAULT_PORT);
        if (c instanceof AndroidWebhookConnector a && !a.endpoints().isEmpty()) {
            port = a.endpoints().getLast().getPort(); // con puerto 0 (pruebas) es el realmente asignado
        }
        return (tls ? "https" : "http") + "://" + lanIp + ":" + port + AndroidWebhookConfig.EVENTS_PATH;
    }

    /** Vuelve a escuchar solo en este equipo y reinicia el receptor. */
    public static void revert(AppContext ctx) {
        revert(ctx, null);
    }

    static void revert(AppContext ctx, com.vivanetpay.integration.ConnectorProvider provider) {
        disable(ctx.config());
        Bootstrap.restartConnector(ctx, ConnectorType.ANDROID_WEBHOOK, provider);
    }

    /** Valor de bindAddress para una IP LAN: siempre conserva el loopback. */
    public static String bindValue(String lanIp) {
        return LOOPBACK + "," + LanAddresses.requirePrivate(lanIp);
    }

    /** Guarda la IP elegida (validada como privada) y devuelve el valor anterior (puede ser null = por defecto). */
    public static String persist(AppConfig config, String lanIp) {
        String previous = config.get(BIND_KEY, null);
        Map<String, String> changes = new LinkedHashMap<>();
        changes.put(BIND_KEY, bindValue(lanIp));
        config.update(changes);
        return previous;
    }

    /** Restaura el valor anterior (null = quitar la clave y volver al valor por defecto solo-loopback). */
    public static void restore(AppConfig config, String previous) {
        Map<String, String> changes = new LinkedHashMap<>();
        changes.put(BIND_KEY, previous);
        config.update(changes);
    }

    /** Vuelve a escuchar solo en este equipo. */
    public static void disable(AppConfig config) {
        restore(config, null);
    }

    /** URL exacta para MacroDroid. */
    public static String webhookUrl(String lanIp, int port, boolean tls) {
        return (tls ? "https" : "http") + "://" + LanAddresses.requirePrivate(lanIp) + ":" + port
                + AndroidWebhookConfig.EVENTS_PATH;
    }

    /** IP LAN configurada actualmente en bindAddress (la primera no loopback), o null. */
    public static String configuredLanIp(Map<String, String> androidSettings) {
        String v = androidSettings.get("bindAddress");
        if (v == null) {
            return null;
        }
        for (String a : v.split(",")) {
            String t = a.trim();
            if (LanAddresses.isPrivateIpv4(t)) {
                return t;
            }
        }
        return null;
    }
}
