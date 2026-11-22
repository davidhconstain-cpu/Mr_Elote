package com.vivanetpay.voice;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Codificación segura de texto para el proceso PowerShell persistente: el texto NUNCA se interpola en código.
 * Cada comando viaja por stdin como una línea {@code SPEAK|<volumen>|<velocidad>|<base64(voz)>|<base64(texto)>}
 * donde los campos de texto van en Base64 de UTF-8 (alfabeto sin espacios, comillas ni metacaracteres de PS).
 * Dentro de PS se decodifican con {@code [Convert]::FromBase64String} y se pasan a {@code Speak} como dato.
 */
public final class PowerShellText {
    private PowerShellText() {
    }

    public static String b64(String text) {
        return Base64.getEncoder().encodeToString((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
    }

    public static String fromB64(String b64) {
        return new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
    }

    /** Línea de comando SPEAK lista para stdin (sin salto de línea final). */
    public static String speakCommand(String text, int volume, int rate, String voiceName) {
        int v = Math.max(0, Math.min(100, volume));
        int r = Math.max(-10, Math.min(10, rate));
        return "SPEAK|" + v + "|" + r + "|" + b64(voiceName) + "|" + b64(text);
    }

    /** Línea de comando STOP (cancela lo que se esté reproduciendo). */
    public static String stopCommand() {
        return "STOP";
    }

    /**
     * Script PowerShell que lee comandos de stdin y responde por stdout una línea por comando:
     * {@code READY} al iniciar (o {@code NOVOICE:<motivo>}), {@code OK} o {@code ERR:<mensaje>}.
     * Es una cadena constante: no contiene datos del usuario.
     */
    public static String serverScript() {
        return """
                $ErrorActionPreference = 'Stop'
                [Console]::InputEncoding = [System.Text.Encoding]::UTF8
                [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
                try {
                  Add-Type -AssemblyName System.Speech
                  $synth = New-Object System.Speech.Synthesis.SpeechSynthesizer
                  $synth.SetOutputToDefaultAudioDevice()
                  [Console]::Out.WriteLine('READY')
                } catch {
                  [Console]::Out.WriteLine('NOVOICE:' + ($_.Exception.Message -replace '[\\r\\n]+',' '))
                  exit 2
                }
                [Console]::Out.Flush()
                while (($line = [Console]::In.ReadLine()) -ne $null) {
                  try {
                    $p = $line.Split('|')
                    if ($p[0] -eq 'SPEAK') {
                      $synth.Volume = [int]$p[1]
                      $synth.Rate = [int]$p[2]
                      $name = [System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($p[3]))
                      $text = [System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($p[4]))
                      $voices = $synth.GetInstalledVoices() | Where-Object { $_.Enabled } | ForEach-Object { $_.VoiceInfo }
                      $pick = $null
                      if ($name) { $pick = $voices | Where-Object { $_.Name -eq $name } | Select-Object -First 1 }
                      if (-not $pick) { $pick = $voices | Where-Object { $_.Culture.Name -eq 'es-CO' } | Select-Object -First 1 }
                      if (-not $pick) { $pick = $voices | Where-Object { $_.Culture.Name -like 'es-*' } | Select-Object -First 1 }
                      if ($pick) { $synth.SelectVoice($pick.Name) }
                      $synth.Speak($text)
                      [Console]::Out.WriteLine('OK')
                    } elseif ($p[0] -eq 'STOP') {
                      $synth.SpeakAsyncCancelAll()
                      [Console]::Out.WriteLine('OK')
                    } elseif ($p[0] -eq 'QUIT') {
                      break
                    } else {
                      [Console]::Out.WriteLine('ERR:comando desconocido')
                    }
                  } catch {
                    [Console]::Out.WriteLine('ERR:' + ($_.Exception.Message -replace '[\\r\\n]+',' '))
                  }
                  [Console]::Out.Flush()
                }
                """;
    }

    /** El script codificado para {@code powershell -EncodedCommand} (UTF-16LE en Base64). */
    public static String encodedScript() {
        return Base64.getEncoder().encodeToString(serverScript().getBytes(StandardCharsets.UTF_16LE));
    }
}
