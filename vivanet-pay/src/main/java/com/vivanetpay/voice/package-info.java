/**
 * Voz: cola de anuncios sin solapamientos y síntesis (Flujo E, RF-015, RF-016, RNF-014, IDD §9.3).
 * <p>
 * Contratos: {@link com.vivanetpay.voice.VoiceService} (implementado por
 * {@link com.vivanetpay.voice.AnnouncementQueue}) y {@link com.vivanetpay.voice.SpeechEngine}.
 * El núcleo trae {@link com.vivanetpay.voice.LoggingSpeechEngine} (pruebas/Linux). El AGENTE DE UI/VOZ
 * implementará el motor de Windows (SAPI / System.Speech vía PowerShell o similar) implementando
 * {@code SpeechEngine} y lo inyectará con {@code AppContext.Builder#speechEngine}.
 */
package com.vivanetpay.voice;
