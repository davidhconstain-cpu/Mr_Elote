package com.vivanetpay.ui;

import com.vivanetpay.voice.PowerShellText;
import com.vivanetpay.voice.SpeechEngines;
import com.vivanetpay.voice.VoiceSettings;
import com.vivanetpay.voice.WindowsSpeechEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformIntegrationTest {

    @Test
    void powerShellTextIsBase64AndNeverRawInterpolated() {
        String evil = "Pago'; Remove-Item C:\\* -Recurse; \"$(calc)\" `n |";
        String cmd = PowerShellText.speakCommand(evil, 80, 0, "Microsoft Sabina");
        String[] p = cmd.split("\\|", -1);
        assertThat(p).hasSize(5);
        assertThat(p[0]).isEqualTo("SPEAK");
        assertThat(cmd).doesNotContain("Remove-Item").doesNotContain("calc").doesNotContain("'");
        assertThat(PowerShellText.fromB64(p[4])).isEqualTo(evil);
        assertThat(PowerShellText.fromB64(p[3])).isEqualTo("Microsoft Sabina");
        assertThat(cmd).doesNotContain("\n").doesNotContain("\r");
    }

    @Test
    void powerShellTextHandlesAccentsAndClampsValues() {
        String t = "Pago recibido por 125.000 pesos desde Bancolombia. ñ á é";
        String[] p = PowerShellText.speakCommand(t, 500, -99, null).split("\\|", -1);
        assertThat(p[1]).isEqualTo("100");
        assertThat(p[2]).isEqualTo("-10");
        assertThat(PowerShellText.fromB64(p[4])).isEqualTo(t);
        assertThat(PowerShellText.fromB64(p[3])).isEmpty();
    }

    @Test
    void encodedScriptIsUtf16LeBase64OfConstantScript() {
        String decoded = new String(Base64.getDecoder().decode(PowerShellText.encodedScript()), StandardCharsets.UTF_16LE);
        assertThat(decoded).isEqualTo(PowerShellText.serverScript()).contains("System.Speech").contains("es-CO");
    }

    @Test
    void regArgumentsAreSeparateListEntries() {
        assertThat(WindowsStartup.addArgs("C:\\Program Files\\VivanetPay\\VivanetPay.exe")).containsExactly(WindowsStartup.addArgs("x").get(0), "add",
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run", "/v", "VivanetPay", "/t", "REG_SZ", "/d",
                "\\\"C:\\Program Files\\VivanetPay\\VivanetPay.exe\\\" --minimized", "/f");
        assertThat(WindowsStartup.commandLine("C:\\Program Files\\VivanetPay\\VivanetPay.exe"))
                .isEqualTo("\"C:\\Program Files\\VivanetPay\\VivanetPay.exe\" --minimized");
        assertThat(WindowsStartup.addArgs("x").get(0)).endsWith("reg.exe");
        assertThat(WindowsStartup.deleteArgs()).containsExactly(WindowsStartup.deleteArgs().get(0), "delete",
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run", "/v", "VivanetPay", "/f");
        assertThat(WindowsStartup.queryArgs()).contains("query");
        assertThatThrownBy(() -> WindowsStartup.commandLine("C:\\a\" & calc")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WindowsStartup.commandLine(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void startupIsNoOpOutsideWindows() {
        WindowsStartup s = new WindowsStartup();
        assertThat(s.supported()).isFalse();
        assertThat(s.enable("x.exe").ok()).isFalse();
        assertThat(s.enable("x.exe").message()).contains("solo está disponible en Windows");
        assertThat(s.disable().ok()).isFalse();
        assertThat(s.isEnabled()).isFalse();
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void fallsBackToLoggingEngineOutsideWindows() {
        assertThat(SpeechEngines.create().id()).isEqualTo("logging");
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void windowsStartupRoundTrip() {
        WindowsStartup s = new WindowsStartup();
        assertThat(s.enable("C:\\Temp\\VivanetPayTest.exe").ok()).isTrue();
        try {
            assertThat(s.isEnabled()).isTrue();
            // El valor real del registro debe conservar las comillas alrededor de la ruta (QA: escape de reg.exe)
            Process q = new ProcessBuilder(WindowsStartup.queryArgs()).redirectErrorStream(true).start();
            String out = new String(q.getInputStream().readAllBytes());
            assertThat(out).contains("\"C:\\Temp\\VivanetPayTest.exe\" --minimized");
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        } finally {
            assertThat(s.disable().ok()).isTrue();
        }
        assertThat(s.isEnabled()).isFalse();
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void windowsSpeechEngineSpeaks() throws Exception {
        try (WindowsSpeechEngine e = new WindowsSpeechEngine()) {
            try {
                e.speak("Prueba de voz. Pago recibido por 125.000 pesos.", VoiceSettings.defaults());
            } catch (Exception ex) {
                // Las máquinas de CI no tienen tarjeta de sonido: el motor arrancó y respondió,
                // pero Windows no encontró dispositivo de audio. Eso no es un fallo de la app.
                org.junit.jupiter.api.Assumptions.assumeFalse(
                        String.valueOf(ex.getMessage()).contains("Audio device error"),
                        "Sin dispositivo de audio en esta máquina");
                throw ex;
            }
            assertThat(e.isAvailable()).isTrue();
        }
    }

    @Test
    void diagnosticsPackageRedactsLogs(@TempDir Path dir) throws Exception {
        Path logs = dir.resolve("logs");
        Files.createDirectories(logs);
        Files.writeString(logs.resolve("vivanetpay.log"), "2025 ERROR x - fallo refresh_token=1//abc123XYZ correo juan.perez@gmail.com\n"
                + "2025 INFO ok\n", StandardCharsets.UTF_8);
        assertThat(DiagnosticsPackage.lastError(logs)).hasValueSatisfying(l ->
                assertThat(l).doesNotContain("abc123XYZ").doesNotContain("juan.perez"));
        Path zip = dir.resolve("d.zip");
        DiagnosticsPackage.create(logs, "token=secretvalue123", zip);
        try (var zf = new java.util.zip.ZipFile(zip.toFile())) {
            String logText = new String(zf.getInputStream(zf.getEntry("logs/vivanetpay.log")).readAllBytes(), StandardCharsets.UTF_8);
            assertThat(logText).doesNotContain("abc123XYZ").doesNotContain("juan.perez").contains("INFO ok");
            String sum = new String(zf.getInputStream(zf.getEntry("resumen.txt")).readAllBytes(), StandardCharsets.UTF_8);
            assertThat(sum).doesNotContain("secretvalue123");
        }
    }

    @Test
    void mainAppClassesLoad() throws Exception {
        assertThat(Class.forName("com.vivanetpay.ui.MainApp")).isNotNull();
        assertThat(Class.forName("com.vivanetpay.ui.Launcher")).isNotNull();
        assertThat(Class.forName("com.vivanetpay.ui.MainWindow")).isNotNull();
    }
}
