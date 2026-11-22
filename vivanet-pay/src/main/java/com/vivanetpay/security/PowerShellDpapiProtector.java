package com.vivanetpay.security;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * DPAPI ({@code System.Security.Cryptography.ProtectedData}, ámbito {@code CurrentUser}) mediante Windows PowerShell,
 * sin dependencias nativas.
 * <ul>
 *   <li>Se usa la ruta absoluta de {@code powershell.exe} en {@code %SystemRoot%} (evita secuestro por PATH).</li>
 *   <li>El script (sin datos) va en {@code -EncodedCommand}; modo, entropía y datos van en Base64 por stdin.</li>
 *   <li>stdout = resultado en Base64. stderr se descarta y nunca se incluye en excepciones.</li>
 * </ul>
 */
public final class PowerShellDpapiProtector implements DataProtector {
    private static final String SCRIPT = String.join("\n",
            "$ErrorActionPreference = 'Stop'",
            "Add-Type -AssemblyName System.Security",
            "$mode = [Console]::In.ReadLine()",
            "$ent = [Convert]::FromBase64String([Console]::In.ReadLine())",
            "$data = [Convert]::FromBase64String([Console]::In.ReadLine())",
            "$scope = [System.Security.Cryptography.DataProtectionScope]::CurrentUser",
            "if ($mode -eq 'P') { $out = [System.Security.Cryptography.ProtectedData]::Protect($data, $ent, $scope) }",
            "elseif ($mode -eq 'U') { $out = [System.Security.Cryptography.ProtectedData]::Unprotect($data, $ent, $scope) }",
            "else { exit 3 }",
            "[Console]::Out.Write([Convert]::ToBase64String($out))",
            "exit 0");
    private static final String ENCODED_SCRIPT =
            Base64.getEncoder().encodeToString(SCRIPT.getBytes(StandardCharsets.UTF_16LE));

    private final String executable;
    private final Duration timeout;

    public PowerShellDpapiProtector() {
        this(defaultExecutable(), Duration.ofSeconds(30));
    }

    PowerShellDpapiProtector(String executable, Duration timeout) {
        this.executable = executable;
        this.timeout = timeout;
    }

    static String defaultExecutable() {
        return SystemExecutables.powerShell();
    }

    @Override
    public byte[] protect(byte[] plaintext, byte[] entropy) throws IOException {
        return run('P', plaintext, entropy);
    }

    @Override
    public byte[] unprotect(byte[] blob, byte[] entropy) throws IOException {
        return run('U', blob, entropy);
    }

    private byte[] run(char mode, byte[] data, byte[] entropy) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(List.of(executable, "-NoProfile", "-NonInteractive", "-NoLogo",
                "-ExecutionPolicy", "Bypass", "-EncodedCommand", ENCODED_SCRIPT));
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process p = pb.start();
        byte[] dataB64 = Base64.getEncoder().encode(data);
        byte[] out = null;
        try {
            try (OutputStream stdin = p.getOutputStream()) {
                stdin.write(new byte[]{(byte) mode, '\n'});
                stdin.write(Base64.getEncoder().encode(entropy));
                stdin.write('\n');
                stdin.write(dataB64);
                stdin.write('\n');
            }
            out = readAll(p.getInputStream());
            if (!p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                p.destroyForcibly();
                throw new IOException("DPAPI: PowerShell no respondió a tiempo");
            }
            if (p.exitValue() != 0) {
                throw new IOException("DPAPI: PowerShell terminó con código " + p.exitValue()
                        + (mode == 'U' ? " (¿secreto de otro usuario/equipo o dañado?)" : ""));
            }
            int len = out.length;
            while (len > 0 && Character.isWhitespace(out[len - 1])) {
                len--;
            }
            byte[] trimmed = java.util.Arrays.copyOf(out, len);
            try {
                return Base64.getDecoder().decode(trimmed);
            } catch (IllegalArgumentException e) {
                throw new IOException("DPAPI: salida inesperada de PowerShell");
            } finally {
                SecretBytes.wipe(trimmed);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            p.destroyForcibly();
            throw new IOException("DPAPI: interrumpido", e);
        } finally {
            SecretBytes.wipe(dataB64);
            SecretBytes.wipe(out);
            if (p.isAlive()) {
                p.destroyForcibly();
            }
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) >= 0) {
            bos.write(buf, 0, n);
            if (bos.size() > 1_048_576) {
                throw new IOException("DPAPI: salida demasiado grande");
            }
        }
        byte[] r = bos.toByteArray();
        java.util.Arrays.fill(buf, (byte) 0);
        return r;
    }
}
