package io.micronaut.dev;

import io.micronaut.context.ApplicationContext;
import io.micronaut.dev.manifest.DevManifest;
import io.micronaut.dev.manifest.DevReloadStrategy;
import io.micronaut.runtime.server.EmbeddedServer;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A watcher that recreates, for a change applied in place, a bean the running server received, here a message source
 * the server's error responses read, would destroy the server with it, and stopping the server stops the application.
 * The context keeps the bean instead, and the runtime applies the change by a restart.
 */
class RecreateRunningServerTest {

    @TempDir
    Path project;

    @Test
    void aRecreationThatWouldStopTheServerRestartsTheApplicationInstead() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        Path src = Files.createDirectories(project.resolve("src/main/java/app"));
        Files.writeString(src.resolve("Application.java"), """
            package app;
            public class Application {
                public static void main(String[] args) {
                    io.micronaut.runtime.Micronaut.build(args)
                        .properties(java.util.Map.of("spec.name", "RecreateRunningServerTest", "micronaut.server.port", args[0]))
                        .mainClass(Application.class)
                        .start();
                }
            }
            """);
        Files.writeString(src.resolve("Messages.java"), """
            package app;
            @jakarta.inject.Singleton
            public class Messages extends io.micronaut.context.StaticMessageSource {
            }
            """);
        // a module's reloader: recreates the message source for every class change applied in place
        Files.writeString(src.resolve("MessagesReloader.java"), """
            package app;
            @io.micronaut.context.annotation.Context
            public class MessagesReloader {
                public MessagesReloader(io.micronaut.context.WatchableBeanContext context) {
                    context.classChanges().watch(change -> context.findBean(Messages.class).ifPresent(context::recreate));
                }
            }
            """);
        Files.writeString(src.resolve("HelloController.java"), controller("hello-1"));

        DevRuntime runtime = new MicronautDevMain().launch(DevManifest.load(manifest()), new String[] {String.valueOf(port)});
        try {
            assertEquals(DevReloadStrategy.AUTO, runtime.strategy(), "byte-buddy-agent attaches to the test JVM");
            ApplicationContext context = runtime.awaitGeneration(1, Duration.ofMinutes(1));
            EmbeddedServer server = context.getBean(EmbeddedServer.class);
            awaitServer(port);
            assertEquals("200 hello-1", get(port, "/hello"));

            // a body-only edit is redefined in place, and the reloader's recreation would destroy the server
            Files.writeString(src.resolve("HelloController.java"), controller("hello-2"));
            runtime.reload();
            ApplicationContext restarted = runtime.awaitGeneration(2, Duration.ofMinutes(1));

            assertNotSame(context, restarted);
            assertTrue(restarted.isRunning());
            assertEquals("200 hello-2", get(port, "/hello"));
            // the old generation was stopped by the restart, not by the recreation, which kept its server
            assertFalse(context.isRunning());
            assertFalse(server.isRunning());
        } finally {
            runtime.close();
        }
    }

    private Path manifest() throws IOException {
        Path manifestFile = project.resolve("dev.properties");
        Files.write(project.resolve("cp.argfile"), List.of(System.getProperty("java.class.path").split(File.pathSeparator)));
        Files.writeString(manifestFile, """
            micronaut.dev.main-class=app.Application
            micronaut.dev.strategy=auto
            micronaut.dev.reloadable=build/classes
            micronaut.dev.compile-classpath=@cp.argfile
            micronaut.dev.processor-path=@cp.argfile
            micronaut.dev.sources.java=src/main/java
            micronaut.dev.compile.java.output=build/classes
            micronaut.dev.patch-in-place=false
            """);
        return manifestFile;
    }

    private static String controller(String greeting) {
        return """
            package app;
            @io.micronaut.http.annotation.Controller
            public class HelloController {
                @io.micronaut.http.annotation.Get(value = "/hello", produces = "text/plain")
                public String hello() { return "%s"; }
            }
            """.formatted(greeting);
    }

    private static String get(int port, String path) throws IOException {
        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(60_000);
            socket.getOutputStream().write(("GET " + path + " HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String status = response.substring("HTTP/1.1 ".length(), response.indexOf('\r'));
            return status.substring(0, 3) + " " + response.substring(response.indexOf("\r\n\r\n") + 4);
        }
    }

    private static void awaitServer(int port) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (true) {
            try (Socket ignored = new Socket("localhost", port)) {
                return;
            } catch (IOException e) {
                if (System.nanoTime() > deadline) {
                    throw e;
                }
                Thread.sleep(50);
            }
        }
    }
}
