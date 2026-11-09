package io.micronaut.dev;

import io.micronaut.context.ApplicationContext;
import io.micronaut.dev.manifest.DevManifest;
import io.micronaut.dev.manifest.DevReloadStrategy;
import io.micronaut.runtime.context.scope.refresh.ConfigurationRefresher;
import io.micronaut.runtime.context.scope.refresh.RefreshEvent;
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
import java.util.Map;

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
        // a module's reloader: recreates the message source for every class change applied in place
        assertRestartsInstead("""
            package app;
            @io.micronaut.context.annotation.Context
            public class MessagesReloader {
                public MessagesReloader(io.micronaut.context.WatchableBeanContext context) {
                    context.classChanges().watch(change -> context.findBean(Messages.class).ifPresent(context::recreate));
                }
            }
            """);
    }

    @Test
    void aRecreationByAListenerOfTheCompletedReloadRestartsTheApplicationInstead() throws Exception {
        assertRestartsInstead("""
            package app;
            @jakarta.inject.Singleton
            public class MessagesReloader implements io.micronaut.context.event.ApplicationEventListener<io.micronaut.context.reload.ReloadCompletedEvent> {
                private final io.micronaut.context.WatchableBeanContext context;
                public MessagesReloader(io.micronaut.context.WatchableBeanContext context) {
                    this.context = context;
                }
                @Override
                public void onApplicationEvent(io.micronaut.context.reload.ReloadCompletedEvent event) {
                    context.findBean(Messages.class).ifPresent(context::recreate);
                }
            }
            """);
    }

    @Test
    void aRefreshTheRuntimeDidNotRunRestartsTheApplicationForABeanTheContextKept() throws Exception {
        // the message source the server received watches its configuration, and asks to be recreated when it changes
        int port = freePort();
        writeApplication("""
            package app;
            @jakarta.inject.Singleton
            public class Messages extends io.micronaut.context.StaticMessageSource {
                public Messages(io.micronaut.context.WatchableBeanContext context) {
                    context.configuration("messages").watchReloading(change -> io.micronaut.context.watch.ReloadingConfigurationWatcher.Outcome.RECREATE);
                }
            }
            """);

        DevRuntime runtime = new MicronautDevMain().launch(DevManifest.load(manifest()), new String[] {String.valueOf(port)});
        try {
            ApplicationContext context = runtime.awaitGeneration(1, Duration.ofMinutes(1));
            awaitServer(port);
            assertEquals("200 hello-1", get(port, "/hello"));

            // a configuration client's refresh event, on a thread of its own: no batch of the runtime runs it
            context.publishEvent(new RefreshEvent(Map.of("messages.greeting", "hello")));
            ApplicationContext restarted = runtime.awaitGeneration(2, Duration.ofMinutes(1));
            assertNotSame(context, restarted);
            assertTrue(restarted.isRunning());
            assertFalse(context.isRunning());
            assertEquals("200 hello-1", get(port, "/hello"));

            // what the /refresh endpoint runs, forced
            restarted.getBean(ConfigurationRefresher.class).refreshAll();
            ApplicationContext third = runtime.awaitGeneration(3, Duration.ofMinutes(1));
            assertNotSame(restarted, third);
            assertTrue(third.isRunning());
            assertFalse(restarted.isRunning());
            assertEquals("200 hello-1", get(port, "/hello"));
            assertEquals(0, runtime.redefinitions());
        } finally {
            runtime.close();
        }
    }

    private void assertRestartsInstead(String reloader) throws Exception {
        int port = freePort();
        Path src = writeApplication("""
            package app;
            @jakarta.inject.Singleton
            public class Messages extends io.micronaut.context.StaticMessageSource {
            }
            """);
        Files.writeString(src.resolve("MessagesReloader.java"), reloader);

        DevRuntime runtime = new MicronautDevMain().launch(DevManifest.load(manifest()), new String[] {String.valueOf(port)});
        try {
            assertEquals(DevReloadStrategy.AUTO, runtime.strategy(), "byte-buddy-agent attaches to the test JVM");
            ApplicationContext context = runtime.awaitGeneration(1, Duration.ofMinutes(1));
            EmbeddedServer server = context.getBean(EmbeddedServer.class);
            Object controller = context.getBean(context.getClassLoader().loadClass("app.HelloController"));
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
            // the redefinition became a restart: the old generation's classes got their bodies back, for the requests
            // that finished on it, and it is not counted as applied in place
            assertEquals("hello-1", controller.getClass().getMethod("hello").invoke(controller));
            assertEquals(0, runtime.redefinitions());
            // what the new generation refused while told of the restart that started it needs no other restart: its
            // beans were built from the new classes
            assertFalse(runtime.inPlaceUpdates().restartRequired(restarted));
        } finally {
            runtime.close();
        }
    }

    private Path writeApplication(String messages) throws IOException {
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
        Files.writeString(src.resolve("Messages.java"), messages);
        Files.writeString(src.resolve("HelloController.java"), controller("hello-1"));
        return src;
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
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
