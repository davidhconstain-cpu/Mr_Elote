package com.vivanetpay.integration.gmail;

import com.vivanetpay.application.EventSink;
import com.vivanetpay.application.IngestionResult;
import com.vivanetpay.domain.AnnouncementKind;
import com.vivanetpay.domain.EventStatus;
import com.vivanetpay.domain.RawEvent;
import com.vivanetpay.integration.ConnectorCursorStore;
import com.vivanetpay.security.SecretStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Fakes en memoria para probar el conector sin red. */
final class GmailTestSupport {
    private GmailTestSupport() {
    }

    static final class MemSecrets implements SecretStore {
        final Map<String, char[]> map = new ConcurrentHashMap<>();

        @Override
        public Optional<char[]> get(String key) {
            char[] v = map.get(key);
            return Optional.ofNullable(v == null ? null : v.clone());
        }

        @Override
        public void put(String key, char[] secret) {
            map.put(key, secret.clone());
        }

        @Override
        public void delete(String key) {
            map.remove(key);
        }
    }

    static final class MemCursors implements ConnectorCursorStore {
        final Map<String, String> map = new HashMap<>();
        final List<String> commits = new ArrayList<>();

        @Override
        public Optional<String> loadCursor(String id) {
            return Optional.ofNullable(map.get(id));
        }

        @Override
        public void commitCursor(String id, String cursor) {
            map.put(id, cursor);
            commits.add(cursor);
        }
    }

    /** Sink que deduplica por externalEventId como el núcleo real y puede fallar bajo demanda. */
    static final class RecordingSink implements EventSink {
        final List<RawEvent> received = new ArrayList<>();
        final Map<String, Long> persisted = new LinkedHashMap<>();
        volatile boolean failing;
        private long seq = 1;

        @Override
        public synchronized IngestionResult ingest(RawEvent e) {
            received.add(e);
            if (failing) {
                return new IngestionResult(null, EventStatus.ERROR, null, null, "fallo simulado",
                        AnnouncementKind.NONE, e.correlationId());
            }
            Long existing = persisted.get(e.externalEventId());
            if (existing != null) {
                return new IngestionResult(existing, EventStatus.DUPLICADO, null, existing, "dup",
                        AnnouncementKind.NONE, e.correlationId());
            }
            long id = seq++;
            persisted.put(e.externalEventId(), id);
            return new IngestionResult(id, EventStatus.PENDIENTE_REVISION, null, null, "ok", AnnouncementKind.NONE,
                    e.correlationId());
        }

        @Override
        public CompletableFuture<IngestionResult> ingestAsync(RawEvent e) {
            return CompletableFuture.completedFuture(ingest(e));
        }
    }

    static final class FakeClient implements GmailClient {
        final Map<String, GmailMessage> mailbox = new LinkedHashMap<>();
        final List<String> listResult = new ArrayList<>();
        final List<String> historyAdded = new ArrayList<>();
        String historyId = "100";
        final Deque<IOException> failures = new ArrayDeque<>();
        boolean historyExpired;
        int fullFetches;
        int metadataFetches;
        int apiCalls;
        String email = "titular@example.com";
        RuntimeException runtimeFailure;

        private void maybeFail() throws IOException {
            apiCalls++;
            if (runtimeFailure != null) {
                RuntimeException r = runtimeFailure;
                runtimeFailure = null;
                throw r;
            }
            IOException f = failures.poll();
            if (f != null) {
                throw f;
            }
        }

        @Override
        public String currentHistoryId() throws IOException {
            maybeFail();
            return historyId;
        }

        @Override
        public List<String> listMessageIds(String query, int max) throws IOException {
            maybeFail();
            return new ArrayList<>(listResult);
        }

        @Override
        public HistoryResult listHistory(String start) throws IOException {
            maybeFail();
            if (historyExpired) {
                throw new GmailHistoryExpiredException("expirado");
            }
            return new HistoryResult(new ArrayList<>(historyAdded), historyId);
        }

        @Override
        public GmailMessage getFull(String id) throws IOException {
            maybeFail();
            fullFetches++;
            return mailbox.get(id);
        }

        @Override
        public GmailMessage getMetadata(String id) throws IOException {
            maybeFail();
            metadataFetches++;
            GmailMessage m = mailbox.get(id);
            return m == null ? null : new GmailMessage(m.id(), m.historyId(), m.internalDateMs(), m.labelIds(),
                    m.headers(), null);
        }

        @Override
        public String accountEmail() {
            return email;
        }
    }

    static final class FakeAuth implements GmailAuth {
        boolean credentials = true;
        GmailClient client;
        boolean revoked;
        IOException revokeFailure;

        FakeAuth(GmailClient client) {
            this.client = client;
        }

        @Override
        public boolean hasCredentials() {
            return credentials;
        }

        @Override
        public GmailClient authorize() {
            credentials = true;
            return client;
        }

        @Override
        public GmailClient openClient() throws IOException {
            if (!credentials) {
                throw new GmailAuthException("no autorizado");
            }
            return client;
        }

        @Override
        public void revoke() throws IOException {
            revoked = true;
            credentials = false;
            if (revokeFailure != null) {
                throw revokeFailure;
            }
        }
    }

    static GmailMessage message(String id, String from, String subject, String body) {
        Map<String, String> h = new HashMap<>();
        h.put("from", from);
        h.put("subject", subject);
        h.put("date", "Mon, 24 Nov 2025 10:15:30 -0500");
        h.put("message-id", "<" + id + "@mail>");
        h.put("authentication-results", "mx.google.com; dkim=pass header.d=nequi.com.co; spf=pass; dmarc=pass");
        MessagePart p = MessagePart.text("text/plain", "UTF-8", body.getBytes(StandardCharsets.UTF_8));
        return new GmailMessage(id, "100", 1_764_000_000_000L, List.of("INBOX"), h, p);
    }

    static String detailDump(Object... o) {
        return Arrays.toString(o);
    }
}
