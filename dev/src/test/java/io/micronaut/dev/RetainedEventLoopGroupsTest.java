package io.micronaut.dev;

import io.micronaut.context.ApplicationContext;
import io.micronaut.dev.manifest.DevManifest;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.EventExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The event loop group the HTTP server and the HTTP clients share is retained across restarts: the next generation's
 * server serves its own routes on it, and its clients, with their own filters, connect on it. A change of the event
 * loops' configuration releases it, and the next generation makes a new group from the changed configuration.
 */
class RetainedEventLoopGroupsTest {

    @TempDir
    Path project;

    private int port;
    private Path src;
    private Path config;
    private DevRuntime runtime;

    @BeforeEach
    void setUp() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
    }

    @Test
    void theEventLoopGroupIsRetainedUntilItsConfigurationChanges() throws Exception {
        launch();
        try {
            ApplicationContext first = runtime.context().orElseThrow();
            EventLoopGroup group = first.getBean(EventLoopGroup.class);
            assertEquals("hello-1", get("/hello"));
            assertEquals("retained", get("/loop"));
            assertEquals("hello-1 filter-1 retained", get("/client"));

            // a route and a client filter changed: the new generation's run on the same event loops
            Files.writeString(src.resolve("HelloController.java"), controller("hello-2"));
            Files.writeString(src.resolve("LoopFilter.java"), filter("filter-2"));
            runtime.reload();
            ApplicationContext second = runtime.awaitGeneration(2, Duration.ofMinutes(2));
            awaitServer();
            assertSame(group, second.getBean(EventLoopGroup.class));
            assertFalse(group.isShuttingDown());
            assertEquals("hello-2", get("/hello"));
            assertEquals("retained", get("/loop"));
            assertEquals("hello-2 filter-2 retained", get("/client"));
            assertTrue(runtime.retainedCount() > 0);

            // the default group configured: a new one, of the configured size, and the retained one released
            Files.writeString(config, "micronaut.netty.event-loops.default.num-threads=2\n");
            runtime.changed(List.of(config), List.of());
            ApplicationContext third = runtime.awaitGeneration(3, Duration.ofMinutes(2));
            awaitServer();
            EventLoopGroup configured = third.getBean(EventLoopGroup.class);
            assertNotSame(group, configured);
            assertTrue(group.isShuttingDown());
            assertEquals(2, size(configured));
            assertEquals("hello-2 filter-2 retained", get("/client"));

            // a restart retains the configured group
            runtime.restart();
            ApplicationContext fourth = runtime.awaitGeneration(4, Duration.ofMinutes(2));
            awaitServer();
            assertSame(configured, fourth.getBean(EventLoopGroup.class));

            // resized: only a restart applies it, which the change asks for, and which releases the group
            Files.writeString(config, "micronaut.netty.event-loops.default.num-threads=3\n");
            runtime.changed(List.of(config), List.of());
            ApplicationContext fifth = runtime.awaitGeneration(5, Duration.ofMinutes(2));
            awaitServer();
            EventLoopGroup resized = fifth.getBean(EventLoopGroup.class);
            assertNotSame(configured, resized);
            assertTrue(configured.isShuttingDown());
            assertEquals(3, size(resized));
            assertEquals("retained", get("/loop"));
            assertEquals("hello-2 filter-2 retained", get("/client"));
        } finally {
            runtime.close();
        }
    }

    @Test
    void aGroupTheNextGenerationDoesNotAskForIsShutDownAsThatGenerationStops() throws Exception {
        launch();
        try {
            EventLoopGroup group = runtime.context().orElseThrow().getBean(EventLoopGroup.class);

            // an interceptor of the application's: its group would keep the application's code, and is not retained
            Files.writeString(src.resolve("Interceptor.java"), """
                package app;
                @jakarta.inject.Singleton
                public class Interceptor implements io.micronaut.http.netty.channel.TaskQueueInterceptor {
                    @Override
                    public java.util.Queue<Runnable> wrapTaskQueue(String groupName, java.util.Queue<Runnable> original) {
                        return original;
                    }
                }
                """);
            runtime.reload();
            ApplicationContext second = runtime.awaitGeneration(2, Duration.ofMinutes(2));
            awaitServer();
            EventLoopGroup intercepted = second.getBean(EventLoopGroup.class);
            assertNotSame(group, intercepted);
            assertEquals("hello-1", get("/hello"));

            // the retained group, which generation two did not ask for, went with it, as did the group it made itself
            runtime.restart();
            ApplicationContext third = runtime.awaitGeneration(3, Duration.ofMinutes(2));
            awaitServer();
            assertTrue(group.isShuttingDown());
            assertTrue(intercepted.isShuttingDown());
            assertNotSame(intercepted, third.getBean(EventLoopGroup.class));
            assertEquals("hello-1", get("/hello"));
        } finally {
            runtime.close();
        }
    }

    @Test
    void theRetainedGroupIsShutDownWhenTheRuntimeCloses() throws Exception {
        launch();
        EventLoopGroup group;
        try {
            group = runtime.context().orElseThrow().getBean(EventLoopGroup.class);
            runtime.restart();
            assertSame(group, runtime.awaitGeneration(2, Duration.ofMinutes(2)).getBean(EventLoopGroup.class));
        } finally {
            runtime.close();
        }
        assertTrue(group.isShuttingDown());
    }

    private static int size(EventLoopGroup group) {
        int size = 0;
        for (EventExecutor ignored : group) {
            size++;
        }
        return size;
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
                        .properties(java.util.Map.of("spec.name", "RetainedEventLoopGroupsTest", "micronaut.server.port", args[0]))
                        .mainClass(Application.class)
                        .start();
                }
            }
            """);
        Files.writeString(src.resolve("HelloController.java"), controller("hello-1"));
        Files.writeString(src.resolve("LoopFilter.java"), filter("filter-1"));
        Files.writeString(src.resolve("HelloClient.java"), """
            package app;
            @io.micronaut.http.client.annotation.Client("http://localhost:${micronaut.server.port}")
            public interface HelloClient {
                @io.micronaut.http.annotation.Get(value = "/hello", consumes = "text/plain")
                io.micronaut.http.HttpResponse<String> hello();
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
        runtime = new MicronautDevMain().launch(DevManifest.load(manifestFile), new String[] {String.valueOf(port)});
        awaitServer();
    }

    /**
     * A controller whose {@code /loop} tells whether it runs on the default event loop group, and whose
     * {@code /client} calls {@code /hello} through the declarative client.
     */
    private static String controller(String hello) {
        return """
            package app;
            @io.micronaut.http.annotation.Controller
            public class HelloController {
                private final io.netty.channel.EventLoopGroup group;
                private final HelloClient client;
                HelloController(io.netty.channel.EventLoopGroup group, HelloClient client) {
                    this.group = group;
                    this.client = client;
                }
                @io.micronaut.http.annotation.Get(value = "/hello", produces = "text/plain")
                public String hello() { return "%s"; }
                @io.micronaut.http.annotation.Get(value = "/loop", produces = "text/plain")
                public String loop() { return LoopFilter.onGroup(group) ? "retained" : "elsewhere"; }
                @io.micronaut.http.annotation.Get(value = "/client", produces = "text/plain")
                @io.micronaut.scheduling.annotation.ExecuteOn(io.micronaut.scheduling.TaskExecutors.BLOCKING)
                public String client() {
                    io.micronaut.http.HttpResponse<String> response = client.hello();
                    return response.body() + " " + LoopFilter.seen;
                }
            }
            """.formatted(hello);
    }

    /**
     * A client filter that records its version, and whether it saw the response on the default event loop group.
     */
    private static String filter(String tag) {
        return """
            package app;
            @io.micronaut.http.annotation.ClientFilter("/hello")
            public class LoopFilter {
                static volatile String seen;
                private final io.netty.channel.EventLoopGroup group;
                LoopFilter(io.netty.channel.EventLoopGroup group) {
                    this.group = group;
                }
                @io.micronaut.http.annotation.ResponseFilter
                public void tag(io.micronaut.http.HttpResponse<?> response) {
                    seen = "%s " + (onGroup(group) ? "retained" : "elsewhere");
                }
                static boolean onGroup(io.netty.channel.EventLoopGroup group) {
                    for (io.netty.util.concurrent.EventExecutor executor : group) {
                        if (executor.inEventLoop()) {
                            return true;
                        }
                    }
                    return false;
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
                socket.getOutputStream().write("GET /hello HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
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
