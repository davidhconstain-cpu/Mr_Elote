package com.vivanetpay.integration.gmail;

import com.google.api.client.auth.oauth2.TokenResponseException;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpResponseException;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.History;
import com.google.api.services.gmail.model.HistoryMessageAdded;
import com.google.api.services.gmail.model.ListHistoryResponse;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.google.api.services.gmail.model.Profile;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Implementación real de {@link GmailClient} con la biblioteca oficial de Google. Solo lectura. */
final class GoogleGmailClient implements GmailClient {

    private static final String ME = "me";
    private static final List<String> METADATA_HEADERS = List.of("From", "Subject", "Date", "Message-ID");

    private final Gmail gmail;
    private volatile String email;

    GoogleGmailClient(Gmail gmail) {
        this.gmail = gmail;
    }

    @Override
    public String currentHistoryId() throws IOException {
        try {
            Profile p = gmail.users().getProfile(ME).execute();
            email = p.getEmailAddress();
            return p.getHistoryId().toString();
        } catch (IOException e) {
            throw translate(e, false);
        }
    }

    @Override
    public List<String> listMessageIds(String query, int max) throws IOException {
        List<String> ids = new ArrayList<>();
        String pageToken = null;
        try {
            do {
                ListMessagesResponse r = gmail.users().messages().list(ME).setQ(query)
                        .setMaxResults((long) Math.min(100, max - ids.size())).setPageToken(pageToken).execute();
                if (r.getMessages() != null) {
                    r.getMessages().forEach(m -> ids.add(m.getId()));
                }
                pageToken = r.getNextPageToken();
            } while (pageToken != null && ids.size() < max);
        } catch (IOException e) {
            throw translate(e, false);
        }
        return ids;
    }

    @Override
    public HistoryResult listHistory(String startHistoryId) throws IOException {
        Set<String> ids = new LinkedHashSet<>();
        String latest = startHistoryId;
        String pageToken = null;
        try {
            do {
                ListHistoryResponse r = gmail.users().history().list(ME)
                        .setStartHistoryId(new BigInteger(startHistoryId))
                        .setHistoryTypes(List.of("messageAdded")).setMaxResults(500L).setPageToken(pageToken)
                        .execute();
                if (r.getHistory() != null) {
                    for (History h : r.getHistory()) {
                        if (h.getMessagesAdded() != null) {
                            for (HistoryMessageAdded a : h.getMessagesAdded()) {
                                ids.add(a.getMessage().getId());
                            }
                        }
                    }
                }
                if (r.getHistoryId() != null) {
                    latest = r.getHistoryId().toString();
                }
                pageToken = r.getNextPageToken();
            } while (pageToken != null);
        } catch (IOException e) {
            throw translate(e, true);
        }
        return new HistoryResult(new ArrayList<>(ids), latest);
    }

    @Override
    public GmailMessage getFull(String id) throws IOException {
        try {
            return map(gmail.users().messages().get(ME, id).setFormat("full").execute());
        } catch (IOException e) {
            return notFoundToNull(e);
        }
    }

    @Override
    public GmailMessage getMetadata(String id) throws IOException {
        try {
            return map(gmail.users().messages().get(ME, id).setFormat("metadata")
                    .setMetadataHeaders(METADATA_HEADERS).execute());
        } catch (IOException e) {
            return notFoundToNull(e);
        }
    }

    @Override
    public String accountEmail() {
        return email;
    }

    private GmailMessage notFoundToNull(IOException e) throws IOException {
        if (e instanceof HttpResponseException h && h.getStatusCode() == 404) {
            return null;
        }
        throw translate(e, false);
    }

    private static GmailMessage map(Message m) {
        Map<String, String> headers = new HashMap<>();
        MessagePart payload = null;
        if (m.getPayload() != null) {
            if (m.getPayload().getHeaders() != null) {
                for (MessagePartHeader h : m.getPayload().getHeaders()) {
                    headers.merge(h.getName().toLowerCase(java.util.Locale.ROOT), h.getValue(),
                            (a, b) -> a + "\n" + b);
                }
            }
            payload = mapPart(m.getPayload());
        }
        return new GmailMessage(m.getId(), m.getHistoryId() == null ? null : m.getHistoryId().toString(),
                m.getInternalDate() == null ? 0 : m.getInternalDate(), m.getLabelIds(), headers, payload);
    }

    private static MessagePart mapPart(com.google.api.services.gmail.model.MessagePart p) {
        Map<String, String> headers = new HashMap<>();
        if (p.getHeaders() != null) {
            for (MessagePartHeader h : p.getHeaders()) {
                headers.putIfAbsent(h.getName().toLowerCase(java.util.Locale.ROOT), h.getValue());
            }
        }
        byte[] body = p.getBody() == null ? null : p.getBody().decodeData();
        List<MessagePart> children = new ArrayList<>();
        if (p.getParts() != null) {
            p.getParts().forEach(c -> children.add(mapPart(c)));
        }
        return new MessagePart(p.getMimeType(), p.getFilename(), headers, body, children);
    }

    /** Traduce excepciones de Google a las del conector. */
    static IOException translate(IOException e, boolean history) {
        int status = 0;
        String reason = null;
        String oauth = null;
        if (e instanceof TokenResponseException t) {
            status = t.getStatusCode();
            oauth = t.getDetails() == null ? null : t.getDetails().getError();
        } else if (e instanceof GoogleJsonResponseException g) {
            status = g.getStatusCode();
            if (g.getDetails() != null && g.getDetails().getErrors() != null && !g.getDetails().getErrors().isEmpty()) {
                reason = g.getDetails().getErrors().get(0).getReason();
            }
        } else if (e instanceof HttpResponseException h) {
            status = h.getStatusCode();
        }
        return GmailErrors.classify(status, reason, oauth, history, e);
    }
}
