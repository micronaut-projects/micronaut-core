package io.micronaut.dev;

import io.micronaut.context.ApplicationContext;
import io.micronaut.dev.manifest.DevManifest;
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
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * An edit of a message bundle is read by the running generation's message sources: their caches and the JDK's cache
 * of the bundle are cleared, without a restart. The JDK's cache is cleared for code that reads the bundle itself too.
 */
class MessageBundleReloadTest {

    @TempDir
    Path project;

    @Test
    void anEditedMessageBundleIsServedWithoutARestart() throws Exception {
        Path src = Files.createDirectories(project.resolve("src/main/java/app"));
        Files.writeString(src.resolve("Messages.java"), """
            package app;
            @io.micronaut.context.annotation.Factory
            public class Messages {
                @jakarta.inject.Singleton
                io.micronaut.context.MessageSource messages() {
                    return new io.micronaut.context.i18n.ResourceBundleMessageSource("i18n.messages", java.util.Locale.ENGLISH);
                }
            }
            """);
        Files.writeString(src.resolve("GreetingController.java"), """
            package app;
            @io.micronaut.http.annotation.Controller
            public class GreetingController {
                private final io.micronaut.context.MessageSource messages;
                GreetingController(io.micronaut.context.MessageSource messages) {
                    this.messages = messages;
                }
                @io.micronaut.http.annotation.Get(value = "/greeting/{lang}", produces = "text/plain")
                public String greeting(String lang) {
                    return messages.getMessage("greeting", java.util.Locale.forLanguageTag(lang)).orElse("none");
                }
            }
            """);
        assertEditsServed();
    }

    @Test
    void anEditedBundleReadWithoutAMessageSourceIsServedWithoutARestart() throws Exception {
        Path src = Files.createDirectories(project.resolve("src/main/java/app"));
        // the JDK's cache of the generation's loader, with no ResourceBundleMessageSource to clear it
        Files.writeString(src.resolve("GreetingController.java"), """
            package app;
            @io.micronaut.http.annotation.Controller
            public class GreetingController {
                @io.micronaut.http.annotation.Get(value = "/greeting/{lang}", produces = "text/plain")
                public String greeting(String lang) {
                    return java.util.ResourceBundle.getBundle("i18n.messages", java.util.Locale.forLanguageTag(lang)).getString("greeting");
                }
            }
            """);
        assertEditsServed();
    }

    private void assertEditsServed() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        Path src = project.resolve("src/main/java/app");
        Path i18n = Files.createDirectories(project.resolve("src/main/resources/i18n"));
        Files.writeString(i18n.resolve("messages.properties"), "greeting=hello-1\n");
        Files.writeString(i18n.resolve("messages_de.properties"), "greeting=hallo-1\n");
        Files.writeString(src.resolve("Application.java"), """
            package app;
            public class Application {
                public static void main(String[] args) {
                    io.micronaut.runtime.Micronaut.build(args)
                        .properties(java.util.Map.of("spec.name", "MessageBundleReloadTest", "micronaut.server.port", args[0]))
                        .mainClass(Application.class)
                        .start();
                }
            }
            """);

        DevRuntime runtime = new MicronautDevMain().launch(DevManifest.load(manifest()), new String[] {String.valueOf(port)});
        try {
            ApplicationContext context = runtime.awaitGeneration(1, Duration.ofMinutes(1));
            awaitServer(port);
            assertEquals("200 hello-1", get(port, "/greeting/en"));
            assertEquals("200 hallo-1", get(port, "/greeting/de"));

            Files.writeString(i18n.resolve("messages.properties"), "greeting=hello-2\n");
            assertEquals("200 hello-2", await(port, "/greeting/en", "200 hello-2"));
            // read, and cached again, before its own edit
            assertEquals("200 hallo-1", get(port, "/greeting/de"));
            Files.writeString(i18n.resolve("messages_de.properties"), "greeting=hallo-2\n");
            assertEquals("200 hallo-2", await(port, "/greeting/de", "200 hallo-2"));

            assertEquals(1, runtime.generation());
            assertSame(context, runtime.context().orElseThrow());
        } finally {
            runtime.close();
        }
    }

    private Path manifest() throws IOException {
        Path manifestFile = project.resolve("dev.properties");
        Files.write(project.resolve("cp.argfile"), List.of(System.getProperty("java.class.path").split(File.pathSeparator)));
        Files.writeString(manifestFile, """
            micronaut.dev.main-class=app.Application
            micronaut.dev.reloadable=build/classes
            micronaut.dev.resources.config=src/main/resources
            micronaut.dev.compile-classpath=@cp.argfile
            micronaut.dev.processor-path=@cp.argfile
            micronaut.dev.sources.java=src/main/java
            micronaut.dev.compile.java.output=build/classes
            """);
        return manifestFile;
    }

    private static String await(int port, String path, String expected) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        String last = get(port, path);
        while (!expected.equals(last) && System.nanoTime() < deadline) {
            Thread.sleep(100);
            last = get(port, path);
        }
        return last;
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
