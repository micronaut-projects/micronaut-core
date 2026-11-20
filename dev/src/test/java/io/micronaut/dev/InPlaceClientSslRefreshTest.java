/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.dev;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import io.micronaut.dev.manifest.DevManifest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A change of the clients' settings reaches the clients created afterwards: the SSL settings nested under a
 * service's entry, refreshed in place, and the settings that other configurations copy as they are created, those of
 * the default client and its pool and the shared {@code micronaut.ssl} settings, through a restart.
 */
class InPlaceClientSslRefreshTest {

    @TempDir
    Path project;

    private int port;
    private HttpsServer upstream;
    private Path config;
    private DevRuntime runtime;

    @BeforeEach
    void setUp() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        upstream = secureUpstream();
    }

    @AfterEach
    void tearDown() {
        if (runtime != null) {
            runtime.close();
        }
        upstream.stop(0);
    }

    @Test
    void clientsCreatedAfterAnInPlaceRefreshUseTheNewSslSettings() throws Exception {
        String upstreamUrl = "https://127.0.0.1:" + upstream.getAddress().getPort();
        launch(upstreamUrl, properties(upstreamUrl, true, true, "10s", 10));
        int generation = runtime.generation();

        // the settings nested under the service's entry: refreshed in place, before the service's client is made,
        // which no longer trusts the upstream
        Files.writeString(config, properties(upstreamUrl, false, true, "10s", 10));
        runtime.changed(List.of(config), List.of());
        assertEquals(generation, runtime.generation(), "the change was refreshed in place");
        assertEquals("rejected", get("/service"));

        // the settings of the default client configuration, nested in what the service's configuration copied as it
        // was created: the application restarts, and the default client no longer trusts the upstream
        Files.writeString(config, properties(upstreamUrl, false, false, "10s", 10));
        runtime.changed(List.of(config), List.of());
        runtime.awaitGeneration(++generation, Duration.ofMinutes(2));
        awaitServer();
        assertEquals("rejected", get("/default"));

        // the default connection pool, which the service's pool copies as it is created through the default client
        // configuration: the application restarts, and the service's pool has the new value
        assertEquals("10", get("/service-pool"));
        Files.writeString(config, properties(upstreamUrl, false, false, "10s", 20));
        runtime.changed(List.of(config), List.of());
        runtime.awaitGeneration(++generation, Duration.ofMinutes(2));
        awaitServer();
        assertEquals("20", get("/service-pool"));

        // the shared settings, which the client SSL configuration copies as it is created: a rebind in place would
        // leave the copy as it was, so the application restarts
        Files.writeString(config, properties(upstreamUrl, false, false, "20s", 20));
        runtime.changed(List.of(config), List.of());
        runtime.awaitGeneration(++generation, Duration.ofMinutes(2));
        awaitServer();
        assertEquals("PT20S", get("/handshake-timeout"));
    }

    private static String properties(String upstreamUrl, boolean serviceTrustAll, boolean defaultTrustAll, String handshakeTimeout,
                                     int maxPendingAcquires) {
        return """
            app.name=in-place-ssl
            micronaut.http.services.up.url=%1$s
            micronaut.http.services.up.ssl.insecure-trust-all-certificates=%2$s
            micronaut.http.client.ssl.insecure-trust-all-certificates=%3$s
            micronaut.ssl.handshake-timeout=%4$s
            micronaut.http.client.pool.max-pending-acquires=%5$s
            """.formatted(upstreamUrl, serviceTrustAll, defaultTrustAll, handshakeTimeout, maxPendingAcquires);
    }

    private void launch(String upstreamUrl, String properties) throws Exception {
        Path src = Files.createDirectories(project.resolve("src/main/java/app"));
        Path resources = Files.createDirectories(project.resolve("src/main/resources"));
        config = resources.resolve("application.properties");
        Files.writeString(config, properties);
        Files.writeString(src.resolve("Application.java"), """
            package app;
            public class Application {
                public static void main(String[] args) {
                    io.micronaut.runtime.Micronaut.build(args)
                        .properties(java.util.Map.of("spec.name", "InPlaceClientSslRefreshTest", "micronaut.server.port", args[0], "upstream.url", args[1]))
                        .mainClass(Application.class)
                        .start();
                }
            }
            """);
        Files.writeString(src.resolve("PingController.java"), """
            package app;
            @io.micronaut.http.annotation.Controller
            public class PingController {
                private final io.micronaut.context.BeanContext context;
                PingController(io.micronaut.context.BeanContext context) {
                    this.context = context;
                }
                @io.micronaut.http.annotation.Get(value = "/ping", produces = "text/plain")
                public String ping() { return "pong"; }
                @io.micronaut.http.annotation.Get(value = "/service-pool", produces = "text/plain")
                public String servicePool() {
                    return String.valueOf(context.getBean(io.micronaut.http.client.ServiceHttpClientConfiguration.class, io.micronaut.inject.qualifiers.Qualifiers.byName("up"))
                        .getConnectionPoolConfiguration().getMaxPendingAcquires());
                }
                @io.micronaut.http.annotation.Get(value = "/handshake-timeout", produces = "text/plain")
                public String handshakeTimeout() {
                    return context.getBean(io.micronaut.http.client.DefaultHttpClientConfiguration.class).getSslConfiguration().getHandshakeTimeout().toString();
                }
            }
            """);
        // each controller, and the client it receives, is created on its first request, after the refresh
        Files.writeString(src.resolve("ServiceController.java"), controller("ServiceController", "/service", "\"up\""));
        Files.writeString(src.resolve("DefaultController.java"), controller("DefaultController", "/default", "\"${upstream.url}\""));
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
     * A controller that calls the upstream with the client it receives, and answers "rejected" when the client does
     * not trust the upstream's certificate.
     */
    private static String controller(String name, String path, String client) {
        return """
            package app;
            @io.micronaut.http.annotation.Controller
            @io.micronaut.scheduling.annotation.ExecuteOn(io.micronaut.scheduling.TaskExecutors.BLOCKING)
            public class %1$s {
                private final io.micronaut.http.client.HttpClient client;
                %1$s(@io.micronaut.http.client.annotation.Client(%3$s) io.micronaut.http.client.HttpClient client) {
                    this.client = client;
                }
                @io.micronaut.http.annotation.Get(value = "%2$s", produces = "text/plain")
                public String call() {
                    try {
                        return client.toBlocking().retrieve("/echo");
                    } catch (io.micronaut.http.client.exceptions.HttpClientException e) {
                        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
                            if (cause instanceof javax.net.ssl.SSLHandshakeException) {
                                return "rejected";
                            }
                        }
                        throw e;
                    }
                }
            }
            """.formatted(name, path, client);
    }

    /**
     * An HTTPS upstream with a self-signed certificate for localhost, which the default trust store does not trust.
     */
    private HttpsServer secureUpstream() throws Exception {
        Path keyStore = project.resolve("upstream.p12");
        Process keytool = new ProcessBuilder(
            Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
            "-genkeypair", "-alias", "upstream", "-keyalg", "RSA", "-keysize", "2048", "-validity", "2",
            "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1",
            "-storetype", "PKCS12", "-keystore", keyStore.toString(), "-storepass", "changeit", "-keypass", "changeit"
        ).redirectErrorStream(true).start();
        String output = new String(keytool.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, keytool.waitFor(), output);

        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(keyStore)) {
            store.load(in, "changeit".toCharArray());
        }
        KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init(store, "changeit".toCharArray());
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keyManagers.getKeyManagers(), null, null);

        HttpsServer secure = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        secure.setHttpsConfigurator(new HttpsConfigurator(context));
        secure.createContext("/echo", InPlaceClientSslRefreshTest::echo);
        secure.start();
        return secure;
    }

    private static void echo(HttpExchange exchange) throws IOException {
        byte[] body = "accepted".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/plain");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
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
