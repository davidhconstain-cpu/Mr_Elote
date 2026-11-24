package com.vivanetpay.app;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import com.vivanetpay.application.AnnouncementRecorder;
import com.vivanetpay.application.AppEventBus;
import com.vivanetpay.application.ConnectorMonitor;
import com.vivanetpay.application.DashboardService;
import com.vivanetpay.application.HistoryService;
import com.vivanetpay.application.IngestionService;
import com.vivanetpay.application.ReviewService;
import com.vivanetpay.integration.ConnectorRegistry;
import com.vivanetpay.licensing.LicenseService;
import com.vivanetpay.parsing.DefaultParsers;
import com.vivanetpay.parsing.ParserRegistry;
import com.vivanetpay.parsing.PaymentParser;
import com.vivanetpay.persistence.AuditRepository;
import com.vivanetpay.persistence.ConnectorRepository;
import com.vivanetpay.persistence.Database;
import com.vivanetpay.persistence.DatabaseMaintenance;
import com.vivanetpay.persistence.IngestionWriter;
import com.vivanetpay.persistence.LicenseRepository;
import com.vivanetpay.persistence.PaymentEventRepository;
import com.vivanetpay.persistence.ReportingQueries;
import com.vivanetpay.persistence.SettingsRepository;
import com.vivanetpay.persistence.TransactionRepository;
import com.vivanetpay.persistence.ValidationRepository;
import com.vivanetpay.persistence.VoiceAnnouncementRepository;
import com.vivanetpay.security.InMemorySecretStore;
import com.vivanetpay.security.SecretStore;
import com.vivanetpay.validation.TrustedSourceValidationPolicy;
import com.vivanetpay.validation.EventValidator;
import com.vivanetpay.validation.IdempotencyEngine;
import com.vivanetpay.validation.ValidationPolicy;
import com.vivanetpay.voice.AnnouncementQueue;
import com.vivanetpay.voice.LoggingSpeechEngine;
import com.vivanetpay.voice.SpeechEngine;
import com.vivanetpay.voice.VoiceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Composición manual de dependencias (sin framework). Abre y migra la base al construirse.
 * Es el objeto que la UI recibe para acceder a servicios. {@link #close()} detiene conectores, voz e ingesta.
 * <pre>
 * AppContext ctx = AppContext.builder(AppConfig.load())
 *         .speechEngine(new WindowsSpeechEngine())     // agente UI/voz
 *         .secretStore(new WindowsCredentialStore())    // agente seguridad
 *         .parsers(DefaultParsers.all())                // opcional: es el valor por defecto
 *         .build();
 * </pre>
 */
public final class AppContext implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(AppContext.class);

    private final AppConfig config;
    private final Clock clock;
    private final ObjectMapper json;
    private final Database database;
    private final PaymentEventRepository eventRepository;
    private final TransactionRepository transactionRepository;
    private final ValidationRepository validationRepository;
    private final AuditRepository auditRepository;
    private final VoiceAnnouncementRepository voiceAnnouncementRepository;
    private final ConnectorRepository connectorRepository;
    private final SettingsRepository settingsRepository;
    private final ReportingQueries reportingQueries;
    private final DatabaseMaintenance maintenance;
    private final AppEventBus eventBus;
    private final ParserRegistry parserRegistry;
    private final ValidationPolicy validationPolicy;
    private final SpeechEngine speechEngine;
    private final VoiceService voiceService;
    private final IngestionService ingestionService;
    private final ReviewService reviewService;
    private final DashboardService dashboardService;
    private final HistoryService historyService;
    private final ConnectorRegistry connectorRegistry;
    private final ConnectorMonitor connectorMonitor;
    private final SecretStore secretStore;
    private final LicenseService licenseService;

    private AppContext(Builder b) {
        this.config = b.config;
        this.clock = b.clock;
        // Jackson 3 trae java.time integrado; fechas ISO-8601 (explícito aunque ya es el valor por defecto en 3.x).
        this.json = JsonMapper.builder()
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        this.database = Database.openAndMigrate(config.databaseFile());
        this.eventRepository = new PaymentEventRepository(database);
        this.transactionRepository = new TransactionRepository(database);
        this.validationRepository = new ValidationRepository(database);
        this.auditRepository = new AuditRepository(database);
        this.voiceAnnouncementRepository = new VoiceAnnouncementRepository(database);
        this.connectorRepository = new ConnectorRepository(database, clock);
        this.settingsRepository = new SettingsRepository(database, clock);
        this.reportingQueries = new ReportingQueries(database);
        this.maintenance = new DatabaseMaintenance(database, clock);
        this.eventBus = new AppEventBus();
        // Sin parsers explícitos se usan los reales del MVP (nadie debe olvidarlos); las pruebas pueden sobrescribirlos.
        this.parserRegistry = new ParserRegistry(b.parsers.isEmpty() ? DefaultParsers.all() : b.parsers);
        this.validationPolicy = b.policy != null ? b.policy : new TrustedSourceValidationPolicy(config.policyConfig());
        this.speechEngine = b.speechEngine != null ? b.speechEngine : new LoggingSpeechEngine();
        AnnouncementQueue queue = new AnnouncementQueue(speechEngine, config.voiceSettings(), clock,
                config.announcementMaxAge());
        queue.addOutcomeListener(new AnnouncementRecorder(database, voiceAnnouncementRepository, eventRepository,
                auditRepository, eventBus));
        this.voiceService = queue;
        IngestionWriter writer = new IngestionWriter(eventRepository, transactionRepository, validationRepository,
                auditRepository);
        this.ingestionService = new IngestionService(database, eventRepository, writer, parserRegistry,
                new EventValidator(), new IdempotencyEngine(config.similarityWindow()), validationPolicy, eventBus,
                voiceService, json, clock);
        this.reviewService = new ReviewService(database, eventRepository, transactionRepository, validationRepository,
                auditRepository, reportingQueries, eventBus, voiceService, validationPolicy.config(), clock);
        this.dashboardService = new DashboardService(reportingQueries, clock);
        this.historyService = new HistoryService(reportingQueries);
        this.connectorRegistry = new ConnectorRegistry();
        this.connectorMonitor = new ConnectorMonitor(connectorRepository, auditRepository, eventBus, voiceService,
                config.connectorVoiceAlerts());
        connectorRegistry.addGlobalListener(connectorMonitor);
        this.secretStore = b.secretStore != null ? b.secretStore : new InMemorySecretStore();
        this.licenseService = new LicenseService(database, new LicenseRepository(), auditRepository, clock);
    }

    public static Builder builder(AppConfig config) {
        return new Builder(config);
    }

    /** Constructor de {@link AppContext}. */
    public static final class Builder {
        private final AppConfig config;
        private Clock clock = Clock.systemUTC();
        private SpeechEngine speechEngine;
        private SecretStore secretStore;
        private ValidationPolicy policy;
        private final List<PaymentParser> parsers = new ArrayList<>();

        private Builder(AppConfig config) {
            this.config = Objects.requireNonNull(config, "config");
        }

        public Builder clock(Clock v) { this.clock = Objects.requireNonNull(v); return this; }
        /** Motor de voz (por defecto {@link LoggingSpeechEngine}). */
        public Builder speechEngine(SpeechEngine v) { this.speechEngine = v; return this; }
        /** Almacén de secretos (por defecto en memoria: SOLO pruebas). */
        public Builder secretStore(SecretStore v) { this.secretStore = v; return this; }
        /** Política (por defecto {@link ConservativeValidationPolicy} con {@link AppConfig#policyConfig()}). */
        public Builder validationPolicy(ValidationPolicy v) { this.policy = v; return this; }
        /** Si no se registra ningún parser se usan {@link DefaultParsers#all()}; registrar alguno los sustituye. */
        public Builder parser(PaymentParser p) { this.parsers.add(Objects.requireNonNull(p)); return this; }
        public Builder parsers(List<? extends PaymentParser> ps) { ps.forEach(this::parser); return this; }

        public AppContext build() {
            config.ensureDirectories();
            return new AppContext(this);
        }
    }

    public AppConfig config() { return config; }
    public Clock clock() { return clock; }
    public ObjectMapper json() { return json; }
    public Database database() { return database; }
    public PaymentEventRepository eventRepository() { return eventRepository; }
    public TransactionRepository transactionRepository() { return transactionRepository; }
    public ValidationRepository validationRepository() { return validationRepository; }
    public AuditRepository auditRepository() { return auditRepository; }
    public VoiceAnnouncementRepository voiceAnnouncementRepository() { return voiceAnnouncementRepository; }
    public ConnectorRepository connectorRepository() { return connectorRepository; }
    public SettingsRepository settingsRepository() { return settingsRepository; }
    public ReportingQueries reportingQueries() { return reportingQueries; }
    public DatabaseMaintenance maintenance() { return maintenance; }
    public AppEventBus eventBus() { return eventBus; }
    public ParserRegistry parserRegistry() { return parserRegistry; }
    public ValidationPolicy validationPolicy() { return validationPolicy; }

    /** true si la confirmación automática de fuentes verificadas está activa (DP-01). */
    public boolean automaticValidationEnabled() { return validationPolicy.config().automaticValidationEnabled(); }

    /**
     * Interruptor de Configuración: persiste {@code policy.automaticValidation} y lo aplica de inmediato a los eventos
     * siguientes (no cambia el estado de los ya registrados).
     */
    public void setAutomaticValidation(boolean enabled, String actor) {
        boolean before = automaticValidationEnabled();
        config.set("policy.automaticValidation", Boolean.toString(enabled));
        if (validationPolicy instanceof TrustedSourceValidationPolicy t) {
            t.setAutomaticValidationEnabled(enabled);
        }
        auditRepository.insert(new com.vivanetpay.domain.AuditEntry(null, com.vivanetpay.domain.AuditEntry.ENTITY_SETTING,
                "policy.automaticValidation", com.vivanetpay.domain.AuditEntry.ACTION_SETTING_CHANGE,
                Boolean.toString(before), Boolean.toString(enabled), actor == null ? "operador" : actor,
                "Confirmación automática de pagos de fuentes verificadas", null, clock.instant()));
    }
    public SpeechEngine speechEngine() { return speechEngine; }
    public VoiceService voiceService() { return voiceService; }
    /** Implementación de {@link com.vivanetpay.application.EventSink} para conectores. */
    public IngestionService ingestionService() { return ingestionService; }
    public ReviewService reviewService() { return reviewService; }
    public DashboardService dashboardService() { return dashboardService; }
    public HistoryService historyService() { return historyService; }
    public ConnectorRegistry connectorRegistry() { return connectorRegistry; }
    public SecretStore secretStore() { return secretStore; }
    public LicenseService licenseService() { return licenseService; }

    /** Cierre ordenado: conectores → ingesta → voz. */
    @Override
    public void close() {
        log.info("Cerrando Vivanet Pay");
        connectorRegistry.stopAll();
        ingestionService.close();
        voiceService.close();
    }
}
