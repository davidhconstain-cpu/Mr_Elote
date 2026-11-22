package com.vivanetpay.integration.android;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import com.vivanetpay.domain.ConnectorStatus;
import com.vivanetpay.domain.ConnectorType;
import com.vivanetpay.integration.AbstractConnector;
import com.vivanetpay.integration.ConnectorDependencies;
import com.vivanetpay.security.SecretBytes;
import com.vivanetpay.security.SecretStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Conector Android/MacroDroid (tipo {@code android}, Flujo C): receptor HTTP(S) local con
 * {@code com.sun.net.httpserver}.
 * <p>
 * Endpoints: {@code POST /v1/events} (evento) y {@code GET /health} (estado, sin datos sensibles). Toda la lógica de
 * seguridad vive en {@link WebhookRequestProcessor}.
 * <p>
 * Exposición de red (DP-03/DP-04 PENDIENTES; R-05): por defecto SOLO {@code 127.0.0.1}. Para recibir del teléfono se
 * añade explícitamente la IP LAN del PC en {@code connector.android.bindAddress}. Reglas:
 * <ul>
 *   <li>Loopback: permitido sin TLS.</li>
 *   <li>Red privada (10/8, 172.16/12, 192.168/16, 100.64/10, link-local, fc00::/7) sin TLS: se permite con WARN y
 *       advertencia visible en {@link #securityWarnings()} (IDD §8.1: HTTPS obligatorio fuera de red privada
 *       protegida; la red Wi-Fi debe ser de confianza).</li>
 *   <li>Dirección pública sin TLS: NO arranca (estado ERROR).</li>
 *   <li>Comodín 0.0.0.0/:: solo con {@code allowWildcardBind=true} (desaconsejado).</li>
 * </ul>
 * No se implementa servicio en la nube: ver el punto de extensión en {@link WebhookRequestProcessor}.
 */
public final class AndroidWebhookConnector extends AbstractConnector {
    private static final Logger log = LoggerFactory.getLogger(AndroidWebhookConnector.class);

    static {
        // Anti "slowloris": el servidor JDK no limita por defecto cuánto tarda un cliente en enviar la solicitud/leer la
        // respuesta, y con 2-4 hilos un cliente lento podría agotarlos. Se fijan tiempos máximos (segundos) salvo que
        // el operador ya los haya definido. Las propiedades las lee el JDK al cargar su configuración del servidor.
        for (String prop : new String[]{"sun.net.httpserver.maxReqTime", "sun.net.httpserver.maxRspTime"}) {
            if (System.getProperty(prop) == null) {
                System.setProperty(prop, "30");
            }
        }
    }

    private final ConnectorDependencies deps;
    private final AndroidWebhookConfig config;
    private final WebhookSecretManager secrets;
    private final WebhookRequestProcessor processor;

    private volatile List<HttpServer> servers = List.of();
    private volatile List<URI> endpoints = List.of();
    private volatile List<String> warnings = List.of();
    private volatile SSLContext sslContext;
    private ExecutorService httpExecutor;
    private ExecutorService ingestExecutor;

    public AndroidWebhookConnector(ConnectorDependencies deps) {
        this(deps, AndroidWebhookConfig.from(deps.settings()));
    }

    public AndroidWebhookConnector(ConnectorDependencies deps, AndroidWebhookConfig config) {
        super(deps.connectorId(), ConnectorType.ANDROID_WEBHOOK, deps.clock());
        this.deps = deps;
        this.config = config;
        this.secrets = new WebhookSecretManager(deps.secretStore(), deps.connectorId(), deps.clock(),
                config.rotationGrace());
        this.processor = new WebhookRequestProcessor(deps.connectorId(), config, secrets, deps.sink(), deps.clock(),
                this::isPaused, this::markSuccess);
    }

    @Override
    public String displayName() {
        return "Android";
    }

    @Override
    protected void doStart() {
        if (!servers.isEmpty()) {
            return;
        }
        List<String> warn = new ArrayList<>();
        List<InetAddress> addresses;
        try {
            addresses = resolveBindAddresses(warn);
            secrets.ensureSecret();
            sslContext = config.tlsEnabled() ? loadSsl() : null;
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Receptor Android no iniciado: {}", e.getMessage());
            updateStatus(ConnectorStatus.ERROR, e.getMessage());
            return;
        } catch (RuntimeException e) {
            log.error("Receptor Android no iniciado", e);
            updateStatus(ConnectorStatus.ERROR, "No se pudo preparar el receptor Android. Revise la configuración.");
            return;
        }

        httpExecutor = new ThreadPoolExecutor(2, 4, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(64),
                daemon("android-webhook-http"), new ThreadPoolExecutor.AbortPolicy());
        ingestExecutor = new ThreadPoolExecutor(1, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32),
                daemon("android-webhook-ingest"), new ThreadPoolExecutor.AbortPolicy());
        processor.setIngestExecutor(ingestExecutor);

        List<HttpServer> started = new ArrayList<>();
        List<URI> uris = new ArrayList<>();
        int port = config.port();
        try {
            for (InetAddress addr : addresses) {
                HttpServer server = create(new InetSocketAddress(addr, port));
                server.createContext("/", this::handle);
                server.setExecutor(httpExecutor);
                server.start();
                started.add(server);
                port = server.getAddress().getPort(); // con port=0, las demás direcciones usan el mismo
                uris.add(uri(addr, port));
            }
        } catch (IOException e) {
            started.forEach(s -> s.stop(0));
            shutdownExecutors();
            String msg = "No se pudo abrir el puerto " + config.port() + " del receptor Android ("
                    + e.getClass().getSimpleName() + "). Verifique que no esté en uso y que la IP configurada sea de este equipo.";
            log.error(msg);
            updateStatus(ConnectorStatus.ERROR, msg);
            return;
        }
        servers = List.copyOf(started);
        endpoints = List.copyOf(uris);
        warnings = List.copyOf(warn);
        for (String w : warn) {
            log.warn("Receptor Android: {}", w);
        }
        log.info("Receptor Android escuchando en {} (auth={}, tls={}).", endpoints, config.authMode(), config.tlsEnabled());
        String detail = "Esperando eventos en " + endpoints.get(endpoints.size() - 1)
                + (warn.isEmpty() ? "" : ". Advertencia: " + warn.get(0));
        if (isPaused()) {
            updateStatus(ConnectorStatus.PAUSADO, "Captura en pausa por el usuario");
        } else {
            updateStatus(ConnectorStatus.CONECTADO, detail);
        }
    }

    @Override
    protected void doStop() {
        List<HttpServer> s = servers;
        servers = List.of();
        endpoints = List.of();
        for (HttpServer server : s) {
            server.stop(0);
        }
        shutdownExecutors();
    }

    private void shutdownExecutors() {
        processor.setIngestExecutor(null);
        if (httpExecutor != null) {
            httpExecutor.shutdownNow();
            httpExecutor = null;
        }
        if (ingestExecutor != null) {
            ingestExecutor.shutdown();
            try {
                if (!ingestExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    ingestExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                ingestExecutor.shutdownNow();
            }
            ingestExecutor = null;
        }
    }

    private HttpServer create(InetSocketAddress address) throws IOException {
        if (sslContext == null) {
            return HttpServer.create(address, 32);
        }
        HttpsServer https = HttpsServer.create(address, 32);
        SSLContext ctx = sslContext;
        https.setHttpsConfigurator(new HttpsConfigurator(ctx) {
            @Override
            public void configure(HttpsParameters params) {
                SSLParameters p = ctx.getDefaultSSLParameters();
                p.setProtocols(new String[]{"TLSv1.3", "TLSv1.2"});
                p.setNeedClientAuth(false);
                params.setSSLParameters(p);
            }
        });
        return https;
    }

    private void handle(HttpExchange ex) {
        try (ex) {
            String ip = ex.getRemoteAddress() == null ? "?" : ex.getRemoteAddress().getAddress().getHostAddress();
            var meta = new WebhookRequestProcessor.RequestMeta(ex.getRequestMethod(), ex.getRequestURI().getPath(), ip,
                    name -> ex.getRequestHeaders().get(name));
            WebhookRequestProcessor.Response r = processor.process(meta, max -> readCapped(ex.getRequestBody(), max));
            byte[] out = "HEAD".equals(ex.getRequestMethod()) ? new byte[0] : r.json().getBytes(StandardCharsets.UTF_8);
            var h = ex.getResponseHeaders();
            h.set("Content-Type", "application/json; charset=utf-8");
            h.set("Cache-Control", "no-store");
            h.set("X-Content-Type-Options", "nosniff");
            r.headers().forEach(h::set);
            ex.sendResponseHeaders(r.status(), out.length == 0 ? -1 : out.length);
            if (out.length > 0) {
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(out);
                }
            }
        } catch (IOException e) {
            log.debug("Receptor Android: conexión interrumpida ({})", e.getClass().getSimpleName());
        } catch (RuntimeException e) {
            log.error("Receptor Android: error inesperado atendiendo solicitud", e);
        }
    }

    /** Lee como máximo {@code max + 1} bytes, sin confiar en Content-Length. @return null si excede. */
    static byte[] readCapped(InputStream in, int max) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(Math.min(max, 4096));
        byte[] buf = new byte[4096];
        int total = 0;
        while (true) {
            int n = in.read(buf, 0, Math.min(buf.length, max + 1 - total));
            if (n < 0) {
                return bos.toByteArray();
            }
            total += n;
            if (total > max) {
                return null;
            }
            bos.write(buf, 0, n);
        }
    }

    private List<InetAddress> resolveBindAddresses(List<String> warn) {
        List<InetAddress> out = new ArrayList<>();
        for (String a : config.bindAddresses()) {
            if (!a.matches("\\d{1,3}(\\.\\d{1,3}){3}") && !a.contains(":")) {
                throw new IllegalArgumentException("connector.android.bindAddress debe contener direcciones IP (no nombres): " + a);
            }
            InetAddress addr;
            try {
                addr = InetAddress.getByName(a); // literal IP: sin consulta DNS
            } catch (IOException e) {
                throw new IllegalArgumentException("Dirección IP inválida en connector.android.bindAddress: " + a);
            }
            if (addr.isAnyLocalAddress()) {
                if (!config.allowWildcardBind()) {
                    throw new IllegalArgumentException("Escuchar en todas las interfaces (" + a + ") está bloqueado. "
                            + "Configure la IP LAN concreta del equipo o allowWildcardBind=true bajo su responsabilidad.");
                }
                warn.add("El receptor escucha en TODAS las interfaces de red (" + a + "). No exponga el puerto a Internet.");
                if (!config.tlsEnabled()) {
                    warn.add("Receptor accesible desde la red SIN HTTPS: configure tls.keystore.");
                }
            } else if (!addr.isLoopbackAddress()) {
                if (isPrivate(addr)) {
                    if (!config.tlsEnabled()) {
                        warn.add("Receptor accesible en la red local (" + addr.getHostAddress() + ") SIN HTTPS. "
                                + "Úselo solo en una red Wi-Fi privada de confianza y nunca abra este puerto en el router.");
                    }
                } else if (!config.tlsEnabled()) {
                    throw new IllegalArgumentException("La dirección " + addr.getHostAddress() + " no es privada: se "
                            + "requiere HTTPS (tls.keystore). No exponga el receptor a Internet (IDD §8.1).");
                } else {
                    warn.add("Receptor en dirección no privada (" + addr.getHostAddress() + "). No exponga el puerto a Internet.");
                }
            }
            out.add(addr);
        }
        return out;
    }

    static boolean isPrivate(InetAddress a) {
        if (a.isSiteLocalAddress() || a.isLinkLocalAddress() || a.isLoopbackAddress()) {
            return true;
        }
        byte[] b = a.getAddress();
        if (a instanceof Inet4Address) {
            return (b[0] & 0xFF) == 100 && (b[1] & 0xC0) == 64; // 100.64.0.0/10 (CGNAT / VPN tipo Tailscale)
        }
        if (a instanceof Inet6Address) {
            return (b[0] & 0xFE) == 0xFC; // fc00::/7 (ULA)
        }
        return false;
    }

    private SSLContext loadSsl() {
        char[] pass = deps.secretStore().get(tlsPasswordKey()).orElse(new char[0]);
        try (InputStream in = Files.newInputStream(config.tlsKeystore())) {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(in, pass);
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, pass);
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(kmf.getKeyManagers(), null, new SecureRandom());
            return ctx;
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo cargar el certificado HTTPS del receptor Android (" + config.tlsKeystore()
                    + "): " + e.getClass().getSimpleName() + ". Verifique el archivo PKCS12 y su contraseña en el almacén seguro ("
                    + tlsPasswordKey() + ").");
        } finally {
            SecretBytes.wipe(pass);
        }
    }

    /** Clave del {@link SecretStore} con la contraseña del keystore PKCS12. */
    public String tlsPasswordKey() {
        return "android." + id() + ".tls_keystore_password";
    }

    private static URI uri(InetAddress addr, int port) {
        String host = addr instanceof Inet6Address ? "[" + addr.getHostAddress().replaceAll("%.*$", "") + "]" : addr.getHostAddress();
        return URI.create("http://" + host + ":" + port + AndroidWebhookConfig.EVENTS_PATH);
    }

    // ---------------------------------------------------------------------------------------------------------
    // API para la pantalla Conectores
    // ---------------------------------------------------------------------------------------------------------

    /** URLs de eventos donde escucha (vacía si no está iniciado). */
    public List<URI> endpoints() {
        String scheme = sslContext != null ? "https" : "http";
        return endpoints.stream().map(u -> URI.create(u.toString().replaceFirst("^http", scheme))).toList();
    }

    /** Puerto efectivo (útil con port=0), o -1 si no está iniciado. */
    public int boundPort() {
        List<HttpServer> s = servers;
        return s.isEmpty() ? -1 : s.get(0).getAddress().getPort();
    }

    public boolean isRunning() {
        return !servers.isEmpty();
    }

    public boolean tlsEnabled() {
        return config.tlsEnabled();
    }

    public AuthMode authMode() {
        return config.authMode();
    }

    /** Advertencias de seguridad vigentes (bind no-loopback sin TLS, comodín...). Vacía = configuración segura. */
    public List<String> securityWarnings() {
        return warnings;
    }

    public WebhookStats stats() {
        return processor.stats();
    }

    public AndroidWebhookConfig config() {
        return config;
    }

    /** Secreto vigente para configurarlo en el teléfono. Borrar el arreglo tras mostrarlo; nunca loguear. */
    public char[] revealSecret() {
        return secrets.revealCurrent();
    }

    /** Rota el secreto (el anterior vale durante {@code rotationGraceMinutes}). @return el nuevo secreto. */
    public char[] rotateSecret() {
        return secrets.rotate();
    }

    /** Revoca el secreto de inmediato (sospecha de exposición, IDD §8.3). @return el nuevo secreto. */
    public char[] revokeSecret() {
        return secrets.revokeAndRegenerate();
    }

    /** @return hasta cuándo se acepta el secreto anterior tras una rotación, o null. */
    public Instant previousSecretValidUntil() {
        return secrets.previousValidUntil();
    }

    /**
     * Prueba de recepción de extremo a extremo para la pantalla Conectores: envía al propio receptor, por HTTP(S) y con
     * la autenticación configurada, un evento marcado {@code "test": true}. El receptor lo autentica y valida pero NO lo
     * entrega al núcleo: no crea pagos. Bloqueante (hasta ~5 s): no llamar desde el hilo de UI.
     */
    public WebhookTestResult sendTestEvent() {
        List<URI> eps = endpoints();
        if (eps.isEmpty()) {
            return new WebhookTestResult(false, 0, "El receptor Android no está iniciado.", Duration.ZERO);
        }
        URI target = eps.get(0);
        if (target.getHost().equals("0.0.0.0") || target.getHost().equals("[::]")) {
            target = URI.create(target.toString().replaceFirst("//[^/]+:", "//127.0.0.1:"));
        }
        String body = """
                {"schemaVersion":1,"eventId":"test-%s","source":"ANDROID_NEQUI","app":"vivanet.selftest",\
                "title":"Prueba Vivanet Pay","text":"Evento de prueba de recepción","capturedAt":"%s","test":true}"""
                .formatted(UUID.randomUUID(), clock.instant());
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        HttpRequest.Builder rb = HttpRequest.newBuilder(target).timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes));
        char[] secret = secrets.revealCurrent();
        byte[] key = SecretBytes.toUtf8(secret);
        try {
            if (config.authMode() == AuthMode.HMAC) {
                String ts = Long.toString(clock.instant().getEpochSecond());
                byte[] n = new byte[18];
                new SecureRandom().nextBytes(n);
                String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(n);
                rb.header(WebhookSigner.HEADER_TIMESTAMP, ts)
                        .header(WebhookSigner.HEADER_NONCE, nonce)
                        .header(WebhookSigner.HEADER_SIGNATURE, WebhookSigner.signatureHeader(key, ts, nonce, bytes));
            } else {
                rb.header("Authorization", "Bearer " + new String(secret));
            }
        } finally {
            SecretBytes.wipe(secret);
            SecretBytes.wipe(key);
        }
        long t0 = System.nanoTime();
        try {
            HttpClient.Builder cb = HttpClient.newBuilder().proxy(HttpClient.Builder.NO_PROXY)
                    .connectTimeout(Duration.ofSeconds(3));
            if (sslContext != null) {
                cb.sslContext(selfTrustContext());
            }
            HttpResponse<String> resp = cb.build().send(rb.build(), HttpResponse.BodyHandlers.ofString());
            Duration latency = Duration.ofNanos(System.nanoTime() - t0);
            boolean ok = resp.statusCode() == 200 && resp.body().contains("\"test_ok\"");
            String msg = ok ? "Prueba recibida correctamente (" + latency.toMillis() + " ms). No se registró ningún pago."
                    : "El receptor respondió con código " + resp.statusCode() + ".";
            return new WebhookTestResult(ok, resp.statusCode(), msg, latency);
        } catch (IOException e) {
            return new WebhookTestResult(false, 0, "No se pudo conectar con el receptor: " + e.getClass().getSimpleName(),
                    Duration.ofNanos(System.nanoTime() - t0));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new WebhookTestResult(false, 0, "Prueba interrumpida.", Duration.ZERO);
        } catch (Exception e) {
            return new WebhookTestResult(false, 0, "Error en la prueba: " + e.getClass().getSimpleName(), Duration.ZERO);
        }
    }

    /** Contexto TLS que confía solo en el certificado del propio keystore (autoprueba local). */
    private SSLContext selfTrustContext() throws Exception {
        char[] pass = deps.secretStore().get(tlsPasswordKey()).orElse(new char[0]);
        try (InputStream in = Files.newInputStream(config.tlsKeystore())) {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(in, pass);
            KeyStore trust = KeyStore.getInstance(KeyStore.getDefaultType());
            trust.load(null, null);
            int i = 0;
            for (String alias : java.util.Collections.list(ks.aliases())) {
                var cert = ks.getCertificate(alias);
                if (cert != null) {
                    trust.setCertificateEntry("c" + i++, cert);
                }
            }
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trust);
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, tmf.getTrustManagers(), new SecureRandom());
            return ctx;
        } finally {
            SecretBytes.wipe(pass);
        }
    }

    private static ThreadFactory daemon(String prefix) {
        AtomicInteger n = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, prefix + "-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }

    @Override
    public String toString() {
        return "AndroidWebhookConnector[" + id() + ", " + endpoints + ", auth=" + config.authMode().name().toLowerCase(Locale.ROOT)
                + ", tls=" + config.tlsEnabled() + "]";
    }
}
