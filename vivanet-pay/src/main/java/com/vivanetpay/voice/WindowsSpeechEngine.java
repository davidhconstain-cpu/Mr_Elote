package com.vivanetpay.voice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Motor de voz de Windows: un proceso PowerShell persistente con {@code System.Speech.Synthesis}
 * ({@code SpeechSynthesizer}). Prefiere una voz es-CO, luego es-*, luego la predeterminada.
 * <ul>
 *   <li>Los comandos van por stdin; el texto se pasa en Base64 ({@link PowerShellText}), nunca interpolado.</li>
 *   <li>Si el proceso muere se reinicia en la siguiente llamada (y se reintenta una vez).</li>
 *   <li>{@code speak} es bloqueante con tiempo máximo ({@link #speakTimeoutSeconds}); al vencer se mata el proceso.</li>
 * </ul>
 * Solo funciona en Windows; use {@link SpeechEngines#create()} para elegir el motor según el sistema.
 */
public final class WindowsSpeechEngine implements SpeechEngine, AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(WindowsSpeechEngine.class);

    private final long startTimeoutSeconds;
    private final long speakTimeoutSeconds;
    private final ExecutorService io = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "vivanet-ps-io");
        t.setDaemon(true);
        return t;
    });

    private Process process;
    private BufferedWriter stdin;
    private BufferedReader stdout;
    private volatile boolean available = true;

    public WindowsSpeechEngine() {
        this(20, 60);
    }

    public WindowsSpeechEngine(long startTimeoutSeconds, long speakTimeoutSeconds) {
        this.startTimeoutSeconds = startTimeoutSeconds;
        this.speakTimeoutSeconds = speakTimeoutSeconds;
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    @Override
    public String id() {
        return "windows-system-speech";
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public synchronized void speak(String text, VoiceSettings settings) throws SpeechException {
        if (text == null || text.isBlank()) {
            return;
        }
        String cmd = PowerShellText.speakCommand(text, settings.volume(), settings.rate(), settings.voiceName());
        SpeechException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                ensureProcess();
                String reply = roundTrip(cmd, speakTimeoutSeconds);
                if (reply.equals("OK")) {
                    available = true;
                    return;
                }
                throw new SpeechException("El motor de voz respondió: " + reply);
            } catch (SpeechException e) {
                last = e;
                log.warn("Fallo de voz (intento {}): {}", attempt + 1, e.getMessage());
                killProcess();
            }
        }
        available = false;
        throw last;
    }

    /** Cancela la reproducción en curso (si hay proceso). */
    public synchronized void stopSpeaking() {
        if (process != null && process.isAlive()) {
            try {
                stdin.write(PowerShellText.stopCommand());
                stdin.newLine();
                stdin.flush();
            } catch (IOException e) {
                killProcess();
            }
        }
    }

    private void ensureProcess() throws SpeechException {
        if (process != null && process.isAlive()) {
            return;
        }
        killProcess();
        try {
            ProcessBuilder pb = new ProcessBuilder(List.of("powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive",
                    "-ExecutionPolicy", "Bypass", "-EncodedCommand", PowerShellText.encodedScript()));
            pb.redirectErrorStream(false);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            process = pb.start();
            stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            stdout = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            process = null;
            throw new SpeechException("No se pudo iniciar PowerShell para la voz", e);
        }
        String first = readLine(startTimeoutSeconds);
        if (!"READY".equals(first)) {
            killProcess();
            throw new SpeechException("No hay voz disponible en Windows: " + first);
        }
        log.info("Motor de voz de Windows iniciado");
    }

    private String roundTrip(String command, long timeoutSeconds) throws SpeechException {
        try {
            stdin.write(command);
            stdin.newLine();
            stdin.flush();
        } catch (IOException e) {
            throw new SpeechException("El motor de voz no responde (proceso terminado)", e);
        }
        return readLine(timeoutSeconds);
    }

    private String readLine(long timeoutSeconds) throws SpeechException {
        Future<String> f = io.submit(() -> stdout.readLine());
        try {
            String line = f.get(timeoutSeconds, TimeUnit.SECONDS);
            if (line == null) {
                throw new SpeechException("El proceso de voz terminó inesperadamente");
            }
            return line.trim();
        } catch (TimeoutException e) {
            f.cancel(true);
            throw new SpeechException("Tiempo de espera agotado en el motor de voz");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SpeechException("Interrumpido", e);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new SpeechException("Error leyendo el motor de voz", e.getCause());
        }
    }

    private void killProcess() {
        if (process != null) {
            process.destroyForcibly();
        }
        process = null;
        stdin = null;
        stdout = null;
    }

    @Override
    public synchronized void close() {
        if (process != null && process.isAlive()) {
            try {
                stdin.write("QUIT");
                stdin.newLine();
                stdin.flush();
            } catch (IOException ignored) {
                // se fuerza abajo
            }
        }
        killProcess();
        io.shutdownNow();
    }
}
