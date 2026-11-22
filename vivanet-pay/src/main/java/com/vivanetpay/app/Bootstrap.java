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
                    String id = provider.defaultConnectorId();
                    Connector connector = provider.create(new ConnectorDependencies(id, ctx.ingestionService(),
                            ctx.connectorRepository(), ctx.secretStore(), config.connectorSettings(type),
                            config.dataDir(), ctx.clock()));
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
}
