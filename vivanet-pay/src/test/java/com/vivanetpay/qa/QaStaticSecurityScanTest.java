package com.vivanetpay.qa;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Escaneo estático del código de producción (revisión de seguridad automatizada y repetible): fugas en logs, SQL por
 * concatenación, ejecución de procesos, deserialización y alcances de Gmail. Falla si alguien reintroduce un patrón
 * peligroso.
 */
class QaStaticSecurityScanTest {
    private static final Path MAIN = Path.of("src", "main", "java");

    private record Src(Path path, String text) {
        String name() {
            return MAIN.relativize(path).toString().replace('\\', '/');
        }
    }

    private static List<Src> sources() throws IOException {
        try (Stream<Path> s = Files.walk(MAIN)) {
            List<Src> out = new ArrayList<>();
            for (Path p : s.filter(f -> f.toString().endsWith(".java")).toList()) {
                out.add(new Src(p, Files.readString(p)));
            }
            return out;
        }
    }

    /** Texto de la llamada que empieza en {@code start} (paréntesis balanceados), con los literales de cadena vaciados. */
    private static String call(String src, int start) {
        int depth = 0;
        int j = src.indexOf('(', start);
        int i = j;
        for (; j < src.length(); j++) {
            char c = src.charAt(j);
            if (c == '"') { // saltar literal
                j++;
                while (j < src.length() && src.charAt(j) != '"') {
                    if (src.charAt(j) == '\\') {
                        j++;
                    }
                    j++;
                }
            } else if (c == '(') {
                depth++;
            } else if (c == ')' && --depth == 0) {
                break;
            }
        }
        return src.substring(i, Math.min(j + 1, src.length())).replaceAll("\"(?:[^\"\\\\]|\\\\.)*\"", "\"\"");
    }

    @Test
    void ningunLogRecibeCuerposSecretosTokensNiFirmas() throws IOException {
        Pattern logCall = Pattern.compile("\\blog\\.(info|warn|error|debug|trace)\\(");
        Pattern sensitive = Pattern.compile("(?i)\\b\\w*(rawtext|body|secret|token|password|passwd|refresh|credential|nonce"
                + "|signature|payload|text)\\w*\\b");
        List<String> offenders = new ArrayList<>();
        for (Src s : sources()) {
            Matcher m = logCall.matcher(s.text());
            while (m.find()) {
                String c = call(s.text(), m.start());
                if (sensitive.matcher(c).find() && !s.name().endsWith("voice/LoggingSpeechEngine.java")) {
                    offenders.add(s.name() + ": " + c);
                }
            }
        }
        assertThat(offenders).as("variables sensibles pasadas a log.*").isEmpty();
    }

    @Test
    void sqlNuncaSeArmaConcatenandoValoresEnStatementsDirectos() throws IOException {
        // execute/executeQuery/executeUpdate con "..." + variable  => inyección potencial (los PreparedStatement usan '?')
        Pattern direct = Pattern.compile("\\.(execute|executeQuery|executeUpdate)\\(\\s*\"[^\"]*\"\\s*\\+\\s*+(?!\")");
        List<String> offenders = new ArrayList<>();
        for (Src s : sources()) {
            if (direct.matcher(s.text()).find()) {
                offenders.add(s.name());
            }
        }
        assertThat(offenders).isEmpty();
    }

    @Test
    void procesosSeLanzanPorRutaAbsolutaYSinShell() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Src s : sources()) {
            if (s.name().endsWith("security/SystemExecutables.java")) {
                continue;
            }
            if (s.text().contains("\"powershell.exe\"") || s.text().contains("\"reg.exe\"")) {
                offenders.add(s.name() + ": nombre simple de ejecutable (use SystemExecutables)");
            }
            if (s.text().contains("Runtime.getRuntime().exec") || Pattern.compile("\"(cmd|cmd\\.exe|sh|bash)\"").matcher(s.text()).find()
                    || s.text().contains("\"/c\"") || s.text().contains("\"-c\"")) {
                offenders.add(s.name() + ": ejecución mediante shell");
            }
            if (s.text().contains("ProcessBuilder(") && Pattern.compile("new ProcessBuilder\\(\\s*\"").matcher(s.text()).find()) {
                offenders.add(s.name() + ": ProcessBuilder con una sola cadena (use lista de argumentos)");
            }
        }
        assertThat(offenders).isEmpty();
    }

    @Test
    void laDeserializacionJavaEstaConfinadaAlAlmacenDeTokensConFiltro() throws IOException {
        List<String> users = new ArrayList<>();
        for (Src s : sources()) {
            if (s.text().contains("ObjectInputStream(")) {
                users.add(s.name());
                assertThat(s.text()).as(s.name() + " debe fijar un ObjectInputFilter").contains("setObjectInputFilter");
            }
        }
        assertThat(users).containsOnly("com/vivanetpay/integration/gmail/SecretStoreDataStoreFactory.java");
    }

    @Test
    void gmailSoloPideElAlcanceDeSoloLectura() throws IOException {
        for (Src s : sources()) {
            assertThat(s.text()).as(s.name()).doesNotContain("GMAIL_MODIFY").doesNotContain("GMAIL_SEND")
                    .doesNotContain("GMAIL_COMPOSE").doesNotContain("GMAIL_INSERT").doesNotContain("MAIL_GOOGLE_COM")
                    .doesNotContain("gmail.modify").doesNotContain("gmail.send").doesNotContain("https://mail.google.com/");
            assertThat(s.text()).as(s.name() + ": sin escrituras sobre el buzón")
                    .doesNotContain(".messages().send(").doesNotContain(".messages().trash(").doesNotContain(".messages().modify(")
                    .doesNotContain(".messages().delete(").doesNotContain(".drafts()");
        }
    }

    @Test
    void elRepositorioNoContieneSecretosNiArchivosSensibles() throws IOException {
        Path root = Path.of(".");
        Pattern secretFile = Pattern.compile("(?i)(client_secret.*\\.json|.*\\.p12|.*\\.pfx|.*\\.jks|.*\\.keystore|.*\\.pem"
                + "|.*\\.key|.*\\.secret|.*\\.dpapi|.*\\.db|.*\\.db-wal)");
        Pattern secretText = Pattern.compile("GOCSPX-[A-Za-z0-9_-]{20,}|ya29\\.[A-Za-z0-9_-]{30,}|1//0[A-Za-z0-9_-]{30,}"
                + "|-----BEGIN (RSA |EC )?PRIVATE KEY-----|AIza[0-9A-Za-z_-]{35}");
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> s = Files.walk(root.resolve("src"))) {
            for (Path p : s.filter(Files::isRegularFile).toList()) {
                if (secretFile.matcher(p.getFileName().toString()).matches()) {
                    offenders.add("archivo sensible: " + p);
                    continue;
                }
                String n = p.getFileName().toString();
                if (n.endsWith(".java") || n.endsWith(".md") || n.endsWith(".xml") || n.endsWith(".properties")
                        || n.endsWith(".json") || n.endsWith(".psv") || n.endsWith(".sql")) {
                    if (secretText.matcher(Files.readString(p)).find()) {
                        offenders.add("posible secreto en " + p);
                    }
                }
            }
        }
        assertThat(offenders).isEmpty();
    }
}
