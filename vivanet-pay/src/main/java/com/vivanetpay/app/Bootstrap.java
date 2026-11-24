package com.vivanetpay.app;

import com.vivanetpay.domain.ConnectorType;
import com.vivanetpay.integration.Connector;
import com.vivanetpay.integration.ConnectorDependencies;
import com.vivanetpay.integration.ConnectorProvider;
import com.vivanetpay.licensing.LicenseInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Arranque sin UI (Flujo A pasos 3-5): carga configuración, abre y migra la base, verifica integridad,
 * respalda (si está habilitado), verifica licencia y registra/arranca los conectores habilitados descubiertos por
 * {@link ServiceLoader} ({@link ConnectorProvider}). No bloquea: los conectores arrancan en sus propios hilos.
 * <p>
 * La UI (agente UI) debe llamar a {@link #start(AppContext.Builder)} desde un hilo de fondo y luego mostrar
 * {@link Result#integrityProblems()} / {@link Result#license()} si requieren atención.
 */
public final class Bootstrap {
    private static final Logger log = LoggerFactory.getLogger(Bootstrap.class);

    private Bootstrap() {
    }

    /**
     * Resultado del arranque.
     *
     * @param context           contexto listo
     * @param integrityProblems problemas de integrity_check (vacío = íntegra)
     * @param license           estado de licencia
     * @param startedConnectors conectores registrados y arrancados
     * @param missingProviders  tipos habilitados sin implementación disponible
     */
    public record Result(AppContext context, List<String> integrityProblems, LicenseInfo license,
                         List<String> startedConnectors, List<ConnectorType> missingProviders) {
        public boolean databaseHealthy() {
            return integrityProblems.isEmpty();
        }
    }

    public static Result start(AppConfig config) {
        // Arranque de producción sin UI: voz y almacén de secretos propios del SO; parsers reales por defecto.
        return start(AppContext.builder(config)
                .speechEngine(com.vivanetpay.voice.SpeechEngines.create())
                .secretStore(com.vivanetpay.security.SecretStores.forCurrentOs(config.dataDir())));
    }

    public static Result start(AppContext.Builder builder) {
        AppContext ctx = builder.build();
        AppConfig config = ctx.config();
        log.info("Iniciando Vivanet Pay; datos en {}", config.dataDir());

        List<String> problems = ctx.maintenance().integrityCheck();
        if (!problems.isEmpty()) {
            log.error("La base de datos no pasó integrity_check: {}", problems);
        } else if (config.backupOnStart()) {
            try {
                ctx.maintenance().backupToDirectory(config.backupsDir());
            } catch (RuntimeException e) {
                log.warn("No se pudo crear el respaldo de inicio: {}", e.getMessage());
            }
        }

        LicenseInfo license = ctx.licenseService().check();
        log.info("Licencia: {}", license.status());
        boolean captureAllowed = !config.licenseEnforce() || license.status().allowsCapture();

        Map<ConnectorType, ConnectorProvider> providers = new EnumMap<>(ConnectorType.class);
        for (ConnectorProvider p : ServiceLoader.load(ConnectorProvider.class)) {
            providers.putIfAbsent(p.type(), p);
        }
        List<ConnectorType> missing = new java.util.ArrayList<>();
        List<String> started = new java.util.ArrayList<>();
        if (!captureAllowed) {
            log.warn("Captura deshabilitada por estado de licencia {} (license.enforce=true)", license.status());
        } else if (problems.isEmpty()) {
            for (ConnectorType type : config.enabledConnectors()) {
                ConnectorProvider provider = providers.get(type);
                if (provider == null) {
                    log.warn("Conector {} habilitado pero sin implementación disponible", type);
                    missing.add(type);
                    continue;
                }
                try {
                    Connector connector = create(ctx, provider, type);
                    ctx.connectorRepository().saveState(connector.state());
                    ctx.connectorRegistry().register(connector);
                    started.add(connector.id());
                } catch (RuntimeException e) {
                    log.error("No se pudo crear el conector {}", type, e);
                }
            }
            ctx.connectorRegistry().startAll();
        }
        return new Result(ctx, problems, license, List.copyOf(started), List.copyOf(missing));
    }

    private static Connector create(AppContext ctx, ConnectorProvider provider, ConnectorType type) {
        AppConfig config = ctx.config();
        return provider.create(new ConnectorDependencies(provider.defaultConnectorId(), ctx.ingestionService(),
                ctx.connectorRepository(), ctx.secretStore(), config.connectorSettings(type), config.dataDir(),
                ctx.clock()));
    }

    /**
     * Detiene, vuelve a crear con la configuración actual y arranca el conector del tipo dado (p. ej. tras cambiar
     * {@code connector.android.bindAddress}). Conserva el estado de pausa. Devuelve el conector nuevo, o null si
     * no existía uno de ese tipo registrado o no hay implementación.
     */
    public static Connector restartConnector(AppContext ctx, ConnectorType type) {
        return restartConnector(ctx, type, null);
    }

    /** Como {@link #restartConnector(AppContext, ConnectorType)} con un proveedor explícito (null = descubrirlo por SPI). */
    static Connector restartConnector(AppContext ctx, ConnectorType type, ConnectorProvider explicit) {
        Connector old = ctx.connectorRegistry().all().stream().filter(c -> c.type() == type).findFirst().orElse(null);
        if (old == null) {
            return null;
        }
        ConnectorProvider provider = explicit;
        for (ConnectorProvider p : ServiceLoader.load(ConnectorProvider.class)) {
            if (provider == null && p.type() == type) {
                provider = p;
            }
        }
        if (provider == null) {
            return null;
        }
        boolean paused = old.state().status() == com.vivanetpay.domain.ConnectorStatus.PAUSADO;
        old.stop();
        ctx.connectorRegistry().unregister(old.id());
        Connector fresh = create(ctx, provider, type);
        ctx.connectorRegistry().register(fresh);
        fresh.start();
        if (paused) {
            fresh.pause();
        }
        ctx.connectorRepository().saveState(fresh.state());
        return fresh;
    }
}
