package com.vivanetpay.integration.gmail;

import java.io.IOException;
import java.util.List;

/**
 * Abstracción mínima sobre la API de Gmail (solo lectura). Existe para poder probar el conector sin red.
 * Errores: {@link GmailAuthException}, {@link GmailRateLimitException}, {@link GmailHistoryExpiredException};
 * cualquier otra {@link IOException} se trata como fallo de red.
 */
public interface GmailClient {

    /** historyId actual del buzón (users.getProfile). Se lee ANTES de listar para no dejar huecos. */
    String currentHistoryId() throws IOException;

    /** ids que cumplen la consulta, del más reciente al más antiguo, hasta {@code max}. */
    List<String> listMessageIds(String query, int max) throws IOException;

    /** Mensajes añadidos desde {@code startHistoryId} (todas las páginas). */
    HistoryResult listHistory(String startHistoryId) throws IOException;

    /** Mensaje completo (cabeceras + cuerpo) o null si ya no existe. */
    GmailMessage getFull(String id) throws IOException;

    /** Solo cabeceras y etiquetas (sin cuerpo) o null si ya no existe. */
    GmailMessage getMetadata(String id) throws IOException;

    /** Cuenta autorizada, si se conoce. */
    String accountEmail();

    /**
     * @param addedMessageIds ids añadidos en orden cronológico, sin repetir
     * @param latestHistoryId historyId al que avanzar el cursor tras procesarlos
     */
    record HistoryResult(List<String> addedMessageIds, String latestHistoryId) {
    }
}
