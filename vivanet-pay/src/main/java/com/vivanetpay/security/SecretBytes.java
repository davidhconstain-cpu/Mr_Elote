package com.vivanetpay.security;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Conversión char[] &lt;-&gt; UTF-8 sin pasar por {@link String} (los String no se pueden borrar de memoria). */
public final class SecretBytes {
    private SecretBytes() {
    }

    /** Codifica en UTF-8. El llamador debe borrar el resultado con {@link #wipe(byte[])}. */
    public static byte[] toUtf8(char[] chars) {
        ByteBuffer buf = StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars));
        byte[] out = Arrays.copyOfRange(buf.array(), buf.position(), buf.limit());
        if (buf.hasArray()) {
            Arrays.fill(buf.array(), (byte) 0);
        }
        return out;
    }

    /** Decodifica UTF-8. El llamador debe borrar el resultado con {@link #wipe(char[])}. */
    public static char[] fromUtf8(byte[] bytes) {
        CharBuffer buf = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(bytes));
        char[] out = Arrays.copyOfRange(buf.array(), buf.position(), buf.limit());
        if (buf.hasArray()) {
            Arrays.fill(buf.array(), '\0');
        }
        return out;
    }

    public static void wipe(byte[] b) {
        if (b != null) {
            Arrays.fill(b, (byte) 0);
        }
    }

    public static void wipe(char[] c) {
        if (c != null) {
            Arrays.fill(c, '\0');
        }
    }
}
