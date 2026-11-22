package com.vivanetpay.voice;

/**
 * Preferencias de voz (RF-016). Valores por defecto provisionales (DP-09 pendiente).
 *
 * @param enabled       voz activada
 * @param volume        0..100
 * @param rate          velocidad -10..10 (0 = normal)
 * @param voiceName     nombre de voz preferida (p. ej. es-CO), nullable = predeterminada del sistema
 * @param includeSender incluir el remitente en el anuncio. Por defecto false (DP-09 pendiente)
 */
public record VoiceSettings(boolean enabled, int volume, int rate, String voiceName, boolean includeSender) {
    public VoiceSettings {
        if (volume < 0 || volume > 100) {
            throw new IllegalArgumentException("volume debe estar entre 0 y 100");
        }
        if (rate < -10 || rate > 10) {
            throw new IllegalArgumentException("rate debe estar entre -10 y 10");
        }
    }

    public static VoiceSettings defaults() {
        return new VoiceSettings(true, 80, 0, null, false);
    }

    public VoiceSettings withEnabled(boolean v) {
        return new VoiceSettings(v, volume, rate, voiceName, includeSender);
    }
}
