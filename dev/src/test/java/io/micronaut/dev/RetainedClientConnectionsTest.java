package io.micronaut.dev;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import io.micronaut.context.ApplicationContext;
import io.micronaut.dev.manifest.DevManifest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The idle connections of the HTTP clients are kept across restarts: the next generation's declarative and injected
 * clients, with their own filters, send their requests on the connections the previous generation opened, which the
 * remote sees as no new connection. A change of the clients' configuration releases them, and a client only takes
 * back the connections of clients with the same TLS settings.
 */
class RetainedClientConnectionsTest {

    @TempDir
    Path project;

    private int port;
    private HttpServer upstream;
    /**
     * The connections the upstream server accepted, by the address of their client side.
     */
    private final Set<InetSocketAddress> accepted = ConcurrentHashMap.newKeySet();
    private Path src;
    private Path config;
    private DevRuntime runtime;

    @BeforeEach
    void setUp() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/echo", this::echo);
        upstream.start();
    }

    /**
     * Records the connection of a request and answers with the version of the filter that tagged it.
     */
    private void echo(HttpExchange exchange) throws IOException {
        accepted.add(exchange.getRemoteAddress());
        String filter = exchange.getRequestHeaders().getFirst("X-Filter");
        byte[] body = String.valueOf(filter).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/plain");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    @AfterEach
    void tearDown() {
        upstream.stop(0);
    }

    @Test
    void theClientsTakeBackTheConnectionsOfThePreviousGeneration() throws Exception {
        launch();
        try {
            assertEquals("filter-1", get("/declarative"));
            assertEquals("filter-1", get("/injected"));
            int connections = accepted.size();
            assertTrue(connections > 0);

            // a client filter changed: the new generation's clients apply it, on the connections already open
            Files.writeString(src.resolve("UpstreamFilter.java"), filter("filter-2"));
            runtime.reload();
            runtime.awaitGeneration(2, Duration.ofMinutes(2));
            awaitServer();
            assertEquals("filter-2", get("/declarative"));
            assertEquals("filter-2", get("/injected"));
            assertEquals(connections, accepted.size(), "connections accepted by the upstream: " + accepted);

            // a restart without a change keeps them again
            runtime.restart();
            runtime.awaitGeneration(3, Duration.ofMinutes(2));
            awaitServer();
            assertEquals("filter-2", get("/declarative"));
            assertEquals("filter-2", get("/injected"));
            assertEquals(connections, accepted.size(), "connections accepted by the upstream: " + accepted);

            // the clients' configuration changed: the connections kept go, and the clients connect again
            Files.writeString(config, base("http://127.0.0.1:" + upstream.getAddress().getPort()) + "micronaut.http.client.read-timeout=25s\n");
            runtime.changed(List.of(config), List.of());
            if (runtime.generation() == 3) {
                // refreshed in place, the clients closed their connections: the restart keeps the new ones
                runtime.restart();
            }
            ApplicationContext fourth = runtime.awaitGeneration(4, Duration.ofMinutes(2));
            awaitServer();
            assertEquals(Duration.ofSeconds(25), fourth.getEnvironment().getProperty("micronaut.http.client.read-timeout", Duration.class).orElseThrow());
            assertEquals("filter-2", get("/declarative"));
            assertEquals("filter-2", get("/injected"));
            int reconnected = accepted.size();
            assertTrue(reconnected > connections, "connections accepted by the upstream: " + accepted);

            // and the next restart keeps the new ones
            runtime.restart();
            runtime.awaitGeneration(5, Duration.ofMinutes(2));
            awaitServer();
            assertEquals("filter-2", get("/declarative"));
            assertEquals("filter-2", get("/injected"));
            assertEquals(reconnected, accepted.size(), "connections accepted by the upstream: " + accepted);
        } finally {
            runtime.close();
        }
    }

    @Test
    void aClientTakesBackTheConnectionsOfAClosedClientOnlyWithTheSameTlsSettings() throws Exception {
        HttpsServer secure = secureUpstream();
        try {
            launch("https://127.0.0.1:" + secure.getAddress().getPort());
            try {
                // a client that trusts any certificate connects, and closes with the connection idle
                assertEquals("filter-1", get("/explicit/lax"));
                assertEquals(1, accepted.size(), "connections accepted by the upstream: " + accepted);

                // another client of an equal configuration takes it back
                assertEquals("filter-1", get("/explicit/lax"));
                assertEquals(1, accepted.size(), "connections accepted by the upstream: " + accepted);

                // a client that trusts the default certificates only verifies the remote itself instead of sending its
                // request on the connection the others opened
                assertEquals("rejected", get("/explicit/strict"));
                assertEquals(1, accepted.size(), "connections accepted by the upstream: " + accepted);
            } finally {
                runtime.close();
            }
        } finally {
            secure.stop(0);
        }
    }

    @Test
    void aClientDoesNotTakeBackTheConnectionsOfTlsSettingsTheApplicationNoLongerMakes() throws Exception {
        HttpsServer secure = secureUpstream();
        try {
            Files.createDirectories(project.resolve("src/main/java/app"));
            Files.writeString(project.resolve("src/main/java/app/TrustAll.java"), trustAllListener(true));
            launch("https://127.0.0.1:" + secure.getAddress().getPort());
            try {
                assertEquals("filter-1", get("/lazy/call"));
                assertEquals(1, accepted.size(), "connections accepted by the upstream: " + accepted);

                // the application no longer makes its client trust any certificate
                Files.writeString(src.resolve("TrustAll.java"), trustAllListener(false));
                runtime.reload();
                runtime.awaitGeneration(2, Duration.ofMinutes(2));
                awaitServer();
                assertEquals("rejected", get("/lazy/call"));
                assertEquals(1, accepted.size(), "connections accepted by the upstream: " + accepted);
            } finally {
                runtime.close();
            }
        } finally {
            secure.stop(0);
        }
    }

    @Test
    void aClientDoesNotTakeBackTheConnectionsVerifiedWithATrustStoreReplacedSince() throws Exception {
        HttpsServer secure = secureUpstream();
        try {
            Path trustStore = project.resolve("truststore.p12");
            trustStore(trustStore, project.resolve("upstream.p12"));
            launch("https://127.0.0.1:" + secure.getAddress().getPort(), """
                micronaut.http.client.ssl.trust-store.path=file:%s
                micronaut.http.client.ssl.trust-store.type=PKCS12
                micronaut.http.client.ssl.trust-store.password=changeit
                """.formatted(trustStore));
            try {
                // the trust store has the upstream's certificate
                assertEquals("filter-1", get("/checked"));
                assertEquals(1, accepted.size(), "connections accepted by the upstream: " + accepted);

                // replaced, at the same path, by one that does not, which no configuration change tells
                Path other = project.resolve("other.p12");
                generateKeyPair(other, "other");
                trustStore(trustStore, other);
                runtime.restart();
                runtime.awaitGeneration(2, Duration.ofMinutes(2));
                awaitServer();
                assertEquals("rejected", get("/checked"));
                assertEquals(1, accepted.size(), "connections accepted by the upstream: " + accepted);
            } finally {
                runtime.close();
            }
        } finally {
            secure.stop(0);
        }
    }

    private void launch() throws Exception {
        launch("http://127.0.0.1:" + upstream.getAddress().getPort());
    }

    private void launch(String upstreamUrl) throws Exception {
        launch(upstreamUrl, "");
    }

    /**
     * The configuration of the application, with the URL of the {@code up} service.
     */
    private static String base(String upstreamUrl) {
        return "app.name=retained\nmicronaut.http.services.up.url=" + upstreamUrl + "\n";
    }

    /**
     * A listener of the application that makes the service clients trust any certificate, or leaves them as they are.
     */
    private static String trustAllListener(boolean trustAll) {
        return """
            package app;
            @jakarta.inject.Singleton
            public class TrustAll implements io.micronaut.context.event.BeanCreatedEventListener<io.micronaut.http.client.ServiceHttpClientConfiguration> {
                @Override
                public io.micronaut.http.client.ServiceHttpClientConfiguration onCreated(io.micronaut.context.event.BeanCreatedEvent<io.micronaut.http.client.ServiceHttpClientConfiguration> event) {
                    if (%s) {
                        io.micronaut.http.ssl.ClientSslConfiguration ssl = new io.micronaut.http.ssl.ClientSslConfiguration();
                        ssl.setInsecureTrustAllCertificates(true);
                        event.getBean().setSslConfiguration(ssl);
                    }
                    return event.getBean();
                }
            }
            """.formatted(trustAll);
    }

    private void launch(String upstreamUrl, String properties) throws Exception {
        src = Files.createDirectories(project.resolve("src/main/java/app"));
        Path resources = Files.createDirectories(project.resolve("src/main/resources"));
        config = resources.resolve("application.properties");
        Files.writeString(config, base(upstreamUrl) + properties);
        Files.writeString(src.resolve("Application.java"), """
            package app;
            public class Application {
                public static void main(String[] args) {
                    io.micronaut.runtime.Micronaut.build(args)
                        .properties(java.util.Map.of("spec.name", "RetainedClientConnectionsTest", "micronaut.server.port", args[0], "upstream.url", args[1]))
                        .mainClass(Application.class)
                        .start();
                }
            }
            """);
        Files.writeString(src.resolve("Upstream.java"), """
            package app;
            @io.micronaut.http.client.annotation.Client("${upstream.url}")
            public interface Upstream {
                @io.micronaut.http.annotation.Get(value = "/echo", consumes = "text/plain")
                String echo();
            }
            """);
        Files.writeString(src.resolve("UpstreamFilter.java"), filter("filter-1"));
        Files.writeString(src.resolve("CallController.java"), """
            package app;
            @io.micronaut.http.annotation.Controller
            @io.micronaut.scheduling.annotation.ExecuteOn(io.micronaut.scheduling.TaskExecutors.BLOCKING)
            public class CallController {
                private final Upstream upstream;
                private final io.micronaut.http.client.HttpClient client;
                CallController(Upstream upstream, @io.micronaut.http.client.annotation.Client("${upstream.url}") io.micronaut.http.client.HttpClient client) {
                    this.upstream = upstream;
                    this.client = client;
                }
                @io.micronaut.http.annotation.Get(value = "/declarative", produces = "text/plain")
                public String declarative() { return upstream.echo(); }
                @io.micronaut.http.annotation.Get(value = "/injected", produces = "text/plain")
                public String injected() { return client.toBlocking().retrieve("/echo"); }
                @io.micronaut.http.annotation.Get(value = "/checked", produces = "text/plain")
                public String checked() {
                    try {
                        return client.toBlocking().retrieve("/echo");
                    } catch (io.micronaut.http.client.exceptions.HttpClientException e) {
                        return Rejection.of(e);
                    }
                }
                @io.micronaut.http.annotation.Get(value = "/ping", produces = "text/plain")
                public String ping() { return "pong"; }
            }
            """);
        Files.writeString(src.resolve("ExplicitController.java"), """
            package app;
            @io.micronaut.http.annotation.Controller("/explicit")
            @io.micronaut.scheduling.annotation.ExecuteOn(io.micronaut.scheduling.TaskExecutors.BLOCKING)
            public class ExplicitController {
                private final io.micronaut.context.BeanContext context;
                private final java.net.URI upstream;
                ExplicitController(io.micronaut.context.BeanContext context, @io.micronaut.context.annotation.Value("${upstream.url}") String upstream) {
                    this.context = context;
                    this.upstream = java.net.URI.create(upstream);
                }
                @io.micronaut.http.annotation.Get(value = "/lax", produces = "text/plain")
                public String lax() {
                    io.micronaut.http.ssl.ClientSslConfiguration ssl = new io.micronaut.http.ssl.ClientSslConfiguration();
                    ssl.setInsecureTrustAllCertificates(true);
                    io.micronaut.http.client.DefaultHttpClientConfiguration configuration = new io.micronaut.http.client.DefaultHttpClientConfiguration();
                    configuration.setSslConfiguration(ssl);
                    return call(configuration);
                }
                @io.micronaut.http.annotation.Get(value = "/strict", produces = "text/plain")
                public String strict() {
                    try {
                        return call(new io.micronaut.http.client.DefaultHttpClientConfiguration());
                    } catch (io.micronaut.http.client.exceptions.HttpClientException e) {
                        return Rejection.of(e);
                    }
                }
                private String call(io.micronaut.http.client.HttpClientConfiguration configuration) {
                    io.micronaut.http.client.HttpClient client = context.createBean(io.micronaut.http.client.HttpClient.class, io.micronaut.http.client.LoadBalancer.fixed(upstream), configuration);
                    try {
                        return client.toBlocking().retrieve("/echo");
                    } finally {
                        client.close();
                    }
                }
            }
            """);
        Files.writeString(src.resolve("LazyController.java"), """
            package app;
            @io.micronaut.http.annotation.Controller("/lazy")
            @io.micronaut.scheduling.annotation.ExecuteOn(io.micronaut.scheduling.TaskExecutors.BLOCKING)
            public class LazyController {
                private final io.micronaut.http.client.HttpClient client;
                LazyController(@io.micronaut.http.client.annotation.Client("up") io.micronaut.http.client.HttpClient client) {
                    this.client = client;
                }
                @io.micronaut.http.annotation.Get(value = "/call", produces = "text/plain")
                public String call() {
                    try {
                        return client.toBlocking().retrieve("/echo");
                    } catch (io.micronaut.http.client.exceptions.HttpClientException e) {
                        return Rejection.of(e);
                    }
                }
            }
            """);
        Files.writeString(src.resolve("Rejection.java"), """
            package app;
            final class Rejection {
                /**
                 * "rejected" if the client failed the TLS handshake, as when it does not trust the remote.
                 */
                static String of(io.micronaut.http.client.exceptions.HttpClientException e) {
                    for (Throwable cause = e; cause != null; cause = cause.getCause()) {
                        if (cause instanceof javax.net.ssl.SSLHandshakeException) {
                            return "rejected";
                        }
                    }
                    throw e;
                }
            }
            """);
        Files.write(project.resolve("cp.argfile"), List.of(System.getProperty("java.class.path").split(File.pathSeparator)));
        Path manifestFile = project.resolve("dev.properties");
        Files.writeString(manifestFile, """
            micronaut.dev.main-class=app.Application
            micronaut.dev.strategy=restart
            micronaut.dev.reloadable=build/classes
            micronaut.dev.resources.config=src/main/resources
            micronaut.dev.compile-classpath=@cp.argfile
            micronaut.dev.processor-path=@cp.argfile
            micronaut.dev.sources.java=src/main/java
            micronaut.dev.compile.java.output=build/classes
            micronaut.dev.patch-in-place=false
            """);
        runtime = new MicronautDevMain().launch(DevManifest.load(manifestFile), new String[] {String.valueOf(port), upstreamUrl});
        awaitServer();
    }

    /**
     * An HTTPS upstream, with a self-signed certificate for localhost that the default trust store does not trust, that
     * records its connections as the plain one does.
     */
    private HttpsServer secureUpstream() throws Exception {
        Path keyStore = project.resolve("upstream.p12");
        generateKeyPair(keyStore, "upstream");
        KeyStore store = load(keyStore);
        KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init(store, "changeit".toCharArray());
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keyManagers.getKeyManagers(), null, null);

        HttpsServer secure = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        secure.setHttpsConfigurator(new HttpsConfigurator(context));
        secure.createContext("/echo", this::echo);
        secure.start();
        return secure;
    }

    /**
     * Makes a PKCS12 key store, with the password {@code changeit}, holding a self-signed certificate for localhost.
     */
    private static void generateKeyPair(Path keyStore, String alias) throws Exception {
        Process keytool = new ProcessBuilder(
            Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
            "-genkeypair", "-alias", alias, "-keyalg", "RSA", "-keysize", "2048", "-validity", "2",
            "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1",
            "-storetype", "PKCS12", "-keystore", keyStore.toString(), "-storepass", "changeit", "-keypass", "changeit"
        ).redirectErrorStream(true).start();
        String output = new String(keytool.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, keytool.waitFor(), output);
    }

    /**
     * Writes a PKCS12 trust store, with the password {@code changeit}, that trusts the certificates of a key store.
     */
    private static void trustStore(Path trustStore, Path keyStore) throws Exception {
        KeyStore keys = load(keyStore);
        KeyStore trust = KeyStore.getInstance("PKCS12");
        trust.load(null, null);
        for (String alias : Collections.list(keys.aliases())) {
            trust.setCertificateEntry(alias, keys.getCertificate(alias));
        }
        try (OutputStream out = Files.newOutputStream(trustStore)) {
            trust.store(out, "changeit".toCharArray());
        }
    }

    private static KeyStore load(Path keyStore) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(keyStore)) {
            store.load(in, "changeit".toCharArray());
        }
        return store;
    }

    /**
     * A client filter that tells the upstream its version.
     */
    private static String filter(String tag) {
        return """
            package app;
            @io.micronaut.http.annotation.ClientFilter("/echo")
            public class UpstreamFilter {
                @io.micronaut.http.annotation.RequestFilter
                public void tag(io.micronaut.http.MutableHttpRequest<?> request) {
                    request.header("X-Filter", "%s");
                }
            }
            """.formatted(tag);
    }

    private String get(String path) throws IOException {
        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(60_000);
            socket.getOutputStream().write(("GET " + path + " HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(response.startsWith("HTTP/1.1 200"), path + " answered " + response);
            return response.substring(response.indexOf("\r\n\r\n") + 4);
        }
    }

    private void awaitServer() throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (true) {
            try (Socket socket = new Socket("localhost", port)) {
                socket.setSoTimeout(60_000);
                socket.getOutputStream().write("GET /ping HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                if (socket.getInputStream().readAllBytes().length > 0) {
                    return;
                }
            } catch (IOException e) {
                if (System.nanoTime() > deadline) {
                    throw e;
                }
            }
            Thread.sleep(50);
        }
    }
}
