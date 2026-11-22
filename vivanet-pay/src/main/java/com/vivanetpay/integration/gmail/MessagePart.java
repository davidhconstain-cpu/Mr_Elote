package com.vivanetpay.integration.gmail;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parte MIME ya decodificada (Content-Transfer-Encoding resuelto) de un mensaje de Gmail.
 *
 * @param mimeType mime type en minúsculas (p. ej. "text/plain", "multipart/alternative")
 * @param filename nombre de archivo si es adjunto; vacío si no
 * @param headers  cabeceras de la parte con nombre en minúsculas (primer valor)
 * @param body     contenido binario ya decodificado (puede ser vacío)
 * @param parts    subpartes
 */
public record MessagePart(String mimeType, String filename, Map<String, String> headers, byte[] body,
                          List<MessagePart> parts) {
    public MessagePart {
        mimeType = mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT);
        filename = filename == null ? "" : filename;
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        body = body == null ? new byte[0] : body;
        parts = parts == null ? List.of() : List.copyOf(parts);
    }

    public static MessagePart text(String mimeType, String charset, byte[] body) {
        return new MessagePart(mimeType, "", Map.of("content-type", mimeType + "; charset=" + charset), body, List.of());
    }

    public static MessagePart multipart(String mimeType, List<MessagePart> parts) {
        return new MessagePart(mimeType, "", Map.of(), new byte[0], parts);
    }
}
