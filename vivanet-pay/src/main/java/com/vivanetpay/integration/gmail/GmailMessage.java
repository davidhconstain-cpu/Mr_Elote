package com.vivanetpay.integration.gmail;

import java.util.List;
import java.util.Map;

/**
 * Mensaje de Gmail simplificado.
 *
 * @param id             id del mensaje de Gmail
 * @param historyId      historyId del mensaje
 * @param internalDateMs fecha interna (epoch ms) o 0
 * @param labelIds       etiquetas (para descartar SPAM/TRASH)
 * @param headers        cabeceras de nivel superior; nombre en minúsculas; los valores repetidos se unen con salto de línea
 * @param payload        árbol MIME; null si solo se pidió metadata
 */
public record GmailMessage(String id, String historyId, long internalDateMs, List<String> labelIds,
                           Map<String, String> headers, MessagePart payload) {
    public GmailMessage {
        labelIds = labelIds == null ? List.of() : List.copyOf(labelIds);
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }
}
