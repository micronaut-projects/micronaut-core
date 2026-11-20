package io.micronaut.dev;

import com.sun.net.httpserver.HttpServer;
import io.micronaut.context.ApplicationContext;
import io.micronaut.dev.manifest.DevManifest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The idle connections of the HTTP clients are kept across restarts: the next generation's declarative and injected
 * clients, with their own filters, send their requests on the connections the previous generation opened, which the
 * remote sees as no new connection. A change of the clients' configuration releases them.
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
        upstream.createContext("/echo", exchange -> {
            accepted.add(exchange.getRemoteAddress());
            String filter = exchange.getRequestHeaders().getFirst("X-Filter");
            byte[] body = String.valueOf(filter).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        upstream.start();
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
            Files.writeString(config, "app.name=retained\nmicronaut.http.client.read-timeout=25s\n");
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

    private void launch() throws Exception {
        src = Files.createDirectories(project.resolve("src/main/java/app"));
        Path resources = Files.createDirectories(project.resolve("src/main/resources"));
        config = resources.resolve("application.properties");
        Files.writeString(config, "app.name=retained\n");
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
                @io.micronaut.http.annotation.Get(value = "/ping", produces = "text/plain")
                public String ping() { return "pong"; }
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
        String upstreamUrl = "http://127.0.0.1:" + upstream.getAddress().getPort();
        runtime = new MicronautDevMain().launch(DevManifest.load(manifestFile), new String[] {String.valueOf(port), upstreamUrl});
        awaitServer();
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
