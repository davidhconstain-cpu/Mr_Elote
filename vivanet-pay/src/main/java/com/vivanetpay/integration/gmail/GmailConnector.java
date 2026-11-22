package com.vivanetpay.integration.gmail;

import com.vivanetpay.application.IngestionResult;
import com.vivanetpay.domain.ConnectorStatus;
import com.vivanetpay.domain.ConnectorType;
import com.vivanetpay.domain.EventSource;
import com.vivanetpay.domain.RawEvent;
import com.vivanetpay.integration.AbstractConnector;
import com.vivanetpay.integration.AuthorizableConnector;
import com.vivanetpay.integration.ConnectorDependencies;
import com.vivanetpay.observability.Redactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleSupplier;

/**
 * Conector Gmail (Flujo B, RF-021). Sincronización incremental con {@code history.list}, sondeo en un hilo de
 * fondo, reintentos con backoff exponencial + jitter (Flujo F) y estados: 401/invalid_grant ->
 * REQUIERE_AUTORIZACION (sin bloquear la app), red -> DESCONECTADO con reintento, 429/rateLimit -> backoff.
 * El cursor (historyId) solo avanza después de que {@code ingest} confirme {@code isPersisted()} (paso 17).
 */
public final class GmailConnector extends AbstractConnector implements AuthorizableConnector {

    private static final Logger log = LoggerFactory.getLogger(GmailConnector.class);
    static final String PERMISSIONS_URL = "https://myaccount.google.com/permissions";
    private static final int RECENT_LIMIT = 1000;
    private static final int MAX_HEADER_VALUE = 2000;
    private static final long RATE_LIMIT_MIN_BACKOFF_MS = 30_000;
    private static final String NEEDS_AUTH = "Gmail no está autorizado. Pulsa 'Autorizar Gmail' para conectar tu cuenta.";

    /** Resultado de un ciclo de sincronización (útil en pruebas). */
    public enum SyncOutcome { OK, SKIPPED, AUTH_REQUIRED, RATE_LIMITED, NETWORK_ERROR, ERROR }

    private final ConnectorDependencies deps;
    private final GmailSettings settings;
    private final GmailAuth auth;
    private final DoubleSupplier random;
    private final Object syncLock = new Object();
    private final Object schedLock = new Object();
    private final Set<String> recent = Collections.newSetFromMap(new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > RECENT_LIMIT;
        }
    });

    private volatile GmailClient client;
    private volatile boolean authRequired;
    private volatile String account;
    private volatile int failures;
    private volatile boolean lastWasRateLimit;
    private ScheduledExecutorService executor;
    private ScheduledFuture<?> next;

    public GmailConnector(ConnectorDependencies deps, GmailSettings settings, GmailAuth auth) {
        this(deps, settings, auth, () -> ThreadLocalRandom.current().nextDouble());
    }

    GmailConnector(ConnectorDependencies deps, GmailSettings settings, GmailAuth auth, DoubleSupplier random) {
        super(deps.connectorId(), ConnectorType.GMAIL, deps.clock());
        this.deps = deps;
        this.settings = settings;
        this.auth = auth;
        this.random = random;
        this.account = deps.secretStore().get(accountKey()).map(String::new).orElse(null);
    }

    @Override
    public String displayName() {
        return "Gmail";
    }

    // ------------------------------------------------------------------ ciclo de vida

    @Override
    protected void doStart() {
        synchronized (schedLock) {
            if (executor != null) {
                return;
            }
            executor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "gmail-sync");
                t.setDaemon(true);
                return t;
            });
        }
        if (!auth.hasCredentials()) {
            authRequired = true;
            updateStatus(ConnectorStatus.REQUIERE_AUTORIZACION, NEEDS_AUTH);
        } else {
            updateStatus(ConnectorStatus.DESCONECTADO, "Conectando con Gmail...");
        }
        scheduleNext(0);
    }

    @Override
    protected void doStop() {
        synchronized (schedLock) {
            if (next != null) {
                next.cancel(false);
            }
            if (executor != null) {
                executor.shutdownNow();
                executor = null;
            }
        }
    }

    @Override
    protected void onResume() {
        scheduleNext(0);
    }

    private void scheduleNext(long delayMs) {
        synchronized (schedLock) {
            if (executor == null) {
                return;
            }
            if (next != null) {
                next.cancel(false);
            }
            try {
                next = executor.schedule(this::cycle, delayMs, TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException e) {
                // detenido
            }
        }
    }

    private void cycle() {
        try {
            syncOnce();
        } catch (RuntimeException e) {
            log.error("Fallo inesperado en el ciclo de Gmail: {}", Redactor.redact(String.valueOf(e)));
        } finally {
            scheduleNext(nextDelayMillis());
        }
    }

    long nextDelayMillis() {
        if (failures <= 0) {
            return settings.pollInterval().toMillis();
        }
        long base = settings.backoffBase().toMillis();
        if (lastWasRateLimit) {
            base = Math.max(base, RATE_LIMIT_MIN_BACKOFF_MS);
        }
        return backoffMillis(failures, base, settings.backoffMax().toMillis(), random.getAsDouble());
    }

    /** Backoff exponencial con tope y jitter (+-20 %). {@code rnd} en [0,1). */
    static long backoffMillis(int failures, long baseMs, long maxMs, double rnd) {
        int exp = Math.min(Math.max(failures - 1, 0), 20);
        double raw = Math.min((double) maxMs, baseMs * Math.pow(2, exp));
        double jittered = raw * (0.8 + 0.4 * rnd);
        return (long) Math.min(jittered, maxMs);
    }

    // ------------------------------------------------------------------ sincronización

    /** Un ciclo de sincronización (síncrono). Lo invoca el planificador; visible para pruebas. */
    SyncOutcome syncOnce() {
        synchronized (syncLock) {
            if (isPaused()) {
                return SyncOutcome.SKIPPED;
            }
            if (authRequired) {
                return SyncOutcome.AUTH_REQUIRED;
            }
            try {
                GmailClient c = client;
                if (c == null) {
                    if (!auth.hasCredentials()) {
                        authRequired = true;
                        updateStatus(ConnectorStatus.REQUIERE_AUTORIZACION, NEEDS_AUTH);
                        return SyncOutcome.AUTH_REQUIRED;
                    }
                    c = auth.openClient();
                    client = c;
                }
                doSync(c);
                if (c.accountEmail() != null && !c.accountEmail().equals(account)) {
                    rememberAccount(c.accountEmail());
                }
                failures = 0;
                lastWasRateLimit = false;
                markSuccess();
                return SyncOutcome.OK;
            } catch (GmailAuthException e) {
                authRequired = true;
                client = null;
                failures = 0;
                log.warn("Gmail requiere autorización: {}", Redactor.redact(e.getMessage()));
                updateStatus(ConnectorStatus.REQUIERE_AUTORIZACION, Redactor.redact(e.getMessage()));
                return SyncOutcome.AUTH_REQUIRED;
            } catch (GmailRateLimitException e) {
                failures++;
                lastWasRateLimit = true;
                log.warn("Gmail limitó las consultas; backoff (intento {})", failures);
                updateStatus(ConnectorStatus.DESCONECTADO,
                        "Gmail limitó temporalmente las consultas. Se reintentará automáticamente.");
                return SyncOutcome.RATE_LIMITED;
            } catch (IngestIncompleteException e) {
                failures++;
                lastWasRateLimit = false;
                log.error("Un correo no quedó persistido; el cursor no avanza y se reintentará");
                updateStatus(ConnectorStatus.ERROR,
                        "No se pudo guardar un correo recibido. Se reintentará automáticamente.");
                return SyncOutcome.ERROR;
            } catch (IOException e) {
                failures++;
                lastWasRateLimit = false;
                log.warn("Sin conexión con Gmail ({}); reintento {}", e.getClass().getSimpleName(), failures);
                updateStatus(ConnectorStatus.DESCONECTADO,
                        "Sin conexión con Gmail. Se reintentará automáticamente.");
                return SyncOutcome.NETWORK_ERROR;
            } catch (RuntimeException e) {
                failures++;
                lastWasRateLimit = false;
                log.error("Error inesperado al sincronizar Gmail: {}", Redactor.redact(String.valueOf(e)));
                updateStatus(ConnectorStatus.ERROR, "Error inesperado al leer Gmail. Se reintentará automáticamente.");
                return SyncOutcome.ERROR;
            }
        }
    }

    private void doSync(GmailClient c) throws IOException {
        Optional<String> cursor = deps.cursorStore().loadCursor(id()).filter(s -> !s.isBlank());
        if (cursor.isEmpty()) {
            fullListSync(c);
            return;
        }
        try {
            incrementalSync(c, cursor.get());
        } catch (GmailHistoryExpiredException e) {
            log.info("historyId expirado; resincronizando con búsqueda acotada");
            fullListSync(c);
        }
    }

    /** Primera vez o historyId expirado: búsqueda acotada; los eventos son históricos (no se anuncian, 9.2). */
    private void fullListSync(GmailClient c) throws IOException {
        String startHistory = c.currentHistoryId(); // antes de listar, para no dejar huecos
        List<String> ids = new ArrayList<>(c.listMessageIds(settings.query(), settings.initialMax()));
        Collections.reverse(ids); // más antiguo primero
        for (String mid : new LinkedHashSet<>(ids)) {
            if (recent.contains(mid)) {
                continue;
            }
            GmailMessage full = c.getFull(mid);
            if (full != null) {
                ingest(full, true);
            }
        }
        if (startHistory != null) {
            deps.cursorStore().commitCursor(id(), startHistory);
        }
    }

    private void incrementalSync(GmailClient c, String cursor) throws IOException {
        GmailClient.HistoryResult h = c.listHistory(cursor);
        for (String mid : h.addedMessageIds()) {
            if (recent.contains(mid)) {
                continue;
            }
            GmailMessage meta = c.getMetadata(mid);
            if (meta == null || meta.labelIds().contains("SPAM") || meta.labelIds().contains("TRASH")
                    || !settings.senderAllowed(meta.headers().get("from"))) {
                continue; // no es de un remitente configurado: no se descarga el cuerpo
            }
            GmailMessage full = c.getFull(mid);
            if (full != null) {
                ingest(full, false);
            }
        }
        // Todos los eventos anteriores al cursor ya devolvieron isPersisted() (paso 17).
        if (h.latestHistoryId() != null && !h.latestHistoryId().equals(cursor)) {
            deps.cursorStore().commitCursor(id(), h.latestHistoryId());
        }
    }

    private void ingest(GmailMessage m, boolean historical) throws IngestIncompleteException {
        IngestionResult r = deps.sink().ingest(toEvent(m, historical));
        if (r == null || !r.isPersisted()) {
            throw new IngestIncompleteException();
        }
        recent.add(m.id());
    }

    RawEvent toEvent(GmailMessage m, boolean historical) {
        Map<String, String> h = m.headers();
        String from = MessageTextExtractor.decodeHeader(h.get("from"));
        String subject = MessageTextExtractor.decodeHeader(h.get("subject"));
        Map<String, String> out = new LinkedHashMap<>();
        put(out, "From", from);
        put(out, "Date", h.get("date"));
        put(out, "Message-ID", h.get("message-id"));
        put(out, "Reply-To", h.get("reply-to"));
        put(out, "Return-Path", h.get("return-path"));
        put(out, "Authentication-Results", h.get("authentication-results"));
        put(out, "ARC-Authentication-Results", h.get("arc-authentication-results"));
        put(out, "Received-SPF", h.get("received-spf"));
        return RawEvent.builder(EventSource.GMAIL, id())
                .externalEventId(m.id())
                .sourceTimestamp(sourceTime(h.get("date"), m.internalDateMs()))
                .receivedAt(clock.instant())
                .subject(trunc(subject, RawEvent.MAX_FIELD_LENGTH))
                .sender(trunc(from, RawEvent.MAX_FIELD_LENGTH))
                .rawText(MessageTextExtractor.extract(m.payload()))
                .headers(out)
                .historical(historical)
                .build();
    }

    private static void put(Map<String, String> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, trunc(value.strip(), MAX_HEADER_VALUE));
        }
    }

    private static String trunc(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    static Instant sourceTime(String dateHeader, long internalDateMs) {
        if (dateHeader != null) {
            String d = dateHeader.replaceAll("\\s*\\([^)]*\\)\\s*$", "").strip();
            try {
                return DateTimeFormatter.RFC_1123_DATE_TIME.parse(d, Instant::from);
            } catch (RuntimeException e) {
                // usar fecha interna
            }
        }
        return internalDateMs > 0 ? Instant.ofEpochMilli(internalDateMs) : null;
    }

    /** El sink no confirmó persistencia: el cursor no debe avanzar. */
    private static final class IngestIncompleteException extends IOException {
        private static final long serialVersionUID = 1L;
    }

    // ------------------------------------------------------------------ AuthorizableConnector

    @Override
    public void authorize() {
        GmailClient c;
        try {
            c = auth.authorize();
        } catch (IOException e) {
            String msg = e instanceof GmailAuthException ? Redactor.redact(e.getMessage())
                    : "No se pudo completar la autorización de Gmail. Revisa tu conexión e inténtalo de nuevo.";
            log.warn("Autorización de Gmail fallida: {}", e.getClass().getSimpleName());
            if (!authRequiredFlag()) {
                updateStatus(ConnectorStatus.REQUIERE_AUTORIZACION, msg);
            }
            throw new IllegalStateException(msg);
        }
        synchronized (syncLock) {
            String email = null;
            try {
                c.currentHistoryId(); // rellena la cuenta
                email = c.accountEmail();
            } catch (IOException e) {
                log.debug("No se pudo leer el perfil tras autorizar: {}", e.getClass().getSimpleName());
            }
            if (email != null && account != null && !email.equalsIgnoreCase(account)) {
                deps.cursorStore().commitCursor(id(), ""); // otra cuenta: el historyId no sirve
                recent.clear();
            }
            if (email != null) {
                rememberAccount(email);
            }
            client = c;
            authRequired = false;
            failures = 0;
        }
        updateStatus(ConnectorStatus.DESCONECTADO, "Gmail autorizado. Sincronizando...");
        scheduleNext(0);
    }

    private boolean authRequiredFlag() {
        return authRequired;
    }

    @Override
    public void revoke() {
        boolean remoteFailed = false;
        synchronized (syncLock) {
            client = null;
            authRequired = true;
            try {
                auth.revoke();
            } catch (IOException e) {
                remoteFailed = true;
                log.warn("No se pudo contactar a Google para revocar: {}", e.getClass().getSimpleName());
            }
            deps.secretStore().delete(accountKey());
            account = null;
            deps.cursorStore().commitCursor(id(), "");
            recent.clear();
        }
        String msg = remoteFailed
                ? "Se borró la autorización local, pero no se pudo avisar a Google. Revoca el acceso de Vivanet Pay en "
                        + PERMISSIONS_URL + " para completar."
                : "Acceso a Gmail revocado. Para confirmarlo, revisa " + PERMISSIONS_URL
                        + ". Puedes volver a autorizar cuando quieras.";
        updateStatus(ConnectorStatus.REQUIERE_AUTORIZACION, msg);
        if (remoteFailed) {
            throw new IllegalStateException(msg);
        }
    }

    @Override
    public boolean isAuthorized() {
        return !authRequired && auth.hasCredentials();
    }

    @Override
    public Optional<String> authorizedAccount() {
        return Optional.ofNullable(account);
    }

    /** Última sincronización exitosa (para la UI). */
    public Optional<Instant> lastSuccessAt() {
        return Optional.ofNullable(state().lastSuccessAt());
    }

    private String accountKey() {
        return "gmail." + deps.connectorId() + ".account";
    }

    private void rememberAccount(String email) {
        account = email;
        char[] chars = email.toCharArray();
        try {
            deps.secretStore().put(accountKey(), chars);
        } finally {
            java.util.Arrays.fill(chars, '\0');
        }
    }
}
