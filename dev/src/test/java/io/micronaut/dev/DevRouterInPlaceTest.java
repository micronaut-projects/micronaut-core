package io.micronaut.dev;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.reload.ReloadStrategy;
import io.micronaut.dev.http.DevRouter;
import io.micronaut.dev.manifest.DevManifest;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.Router;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A body-only edit of the class that declares routes in code is applied in place, by redefining the class, and the
 * route table is rebuilt from the new code without restarting the generation or the server.
 */
class DevRouterInPlaceTest {

    @TempDir
    Path project;

    @Test
    void aBodyOnlyEditOfHttpRoutesRebuildsTheRouteTableInPlace() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        Path src = Files.createDirectories(project.resolve("src/main/java/app"));
        Files.writeString(src.resolve("Application.java"), application());
        Files.writeString(src.resolve("HelloController.java"), controller("hello-1"));
        Files.writeString(src.resolve("Routes.java"), routes("/fn", "fn-1"));

        DevRuntime runtime = new MicronautDevMain().launch(DevManifest.load(manifest()), new String[] {String.valueOf(port)});
        try {
            assertEquals(ReloadStrategy.AUTO, runtime.strategy(), "byte-buddy-agent attaches to the test JVM");
            ApplicationContext context = runtime.awaitGeneration(1, Duration.ofMinutes(1));
            EmbeddedServer server = context.getBean(EmbeddedServer.class);
            DevRouter router = context.getBean(DevRouter.class);
            assertSame(router, context.getBean(Router.class));
            awaitServer(port);
            assertEquals("200 fn-1", get(port, "/fn"));
            assertEquals("200 hello-1", get(port, "/hello"));

            // the path of the route and the body of its handler change, the class keeps its shape: redefined in place,
            // and the routes declared again by the new code
            Files.writeString(src.resolve("Routes.java"), routes("/moved", "moved-2"));
            runtime.reload();
            assertEquals(1, runtime.redefinitions());
            assertEquals(1, runtime.generation());
            assertSame(context, runtime.context().orElseThrow());
            assertEquals(1, router.rebuilds());
            assertEquals("200 moved-2", get(port, "/moved"));
            assertTrue(get(port, "/fn").startsWith("404 "));

            // a controller method's body declares no route: redefined in place, the table is left as it is
            Files.writeString(src.resolve("HelloController.java"), controller("hello-2"));
            runtime.reload();
            assertEquals(2, runtime.redefinitions());
            assertEquals(1, router.rebuilds());
            assertEquals("200 hello-2", get(port, "/hello"));
            assertEquals("200 moved-2", get(port, "/moved"));

            // the same server served every request
            assertSame(server, context.getBean(EmbeddedServer.class));
            assertTrue(server.isRunning());
        } finally {
            runtime.close();
        }
    }

    @Test
    void aBodyOnlyEditOfAnInheritedRouteMethodRebuildsTheRouteTableInPlace() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        Path src = Files.createDirectories(project.resolve("src/main/java/app"));
        Files.writeString(src.resolve("Application.java"), application());
        Files.writeString(src.resolve("BaseRoutes.java"), baseRoutes("/inherited", "inherited-1"));
        Files.writeString(src.resolve("Routes.java"), """
            package app;
            @jakarta.inject.Singleton
            public class Routes extends BaseRoutes {
            }
            """);
        DevRuntime runtime = new MicronautDevMain().launch(DevManifest.load(manifest()), new String[] {String.valueOf(port)});
        try {
            assertEquals(ReloadStrategy.AUTO, runtime.strategy(), "byte-buddy-agent attaches to the test JVM");
            ApplicationContext context = runtime.awaitGeneration(1, Duration.ofMinutes(1));
            DevRouter router = context.getBean(DevRouter.class);
            awaitServer(port);
            assertEquals("200 inherited-1", get(port, "/inherited"));

            // only the superclass changes, and it declares the routes of the bean that extends it
            Files.writeString(src.resolve("BaseRoutes.java"), baseRoutes("/inherited-moved", "inherited-2"));
            runtime.reload();
            assertEquals(1, runtime.redefinitions());
            assertEquals(1, runtime.generation());
            assertSame(context, runtime.context().orElseThrow());
            assertEquals(1, router.rebuilds());
            assertEquals("200 inherited-2", get(port, "/inherited-moved"));
            assertTrue(get(port, "/inherited").startsWith("404 "));
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

    private static String application() {
        return """
            package app;
            public class Application {
                public static void main(String[] args) {
                    io.micronaut.runtime.Micronaut.build(args)
                        .properties(java.util.Map.of("spec.name", "DevRouterInPlaceTest", "micronaut.server.port", args[0]))
                        .mainClass(Application.class)
                        .start();
                }
            }
            """;
    }

    private static String baseRoutes(String path, String body) {
        return """
            package app;
            public abstract class BaseRoutes implements io.micronaut.web.router.builder.HttpRoutes {
                @Override
                public void routes(io.micronaut.web.router.builder.HttpRouteBuilder routes) {
                    routes.GET("%s", (request, variables) -> io.micronaut.http.HttpResponse.ok("%s").contentType("text/plain"));
                }
            }
            """.formatted(path, body);
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

    private static String routes(String path, String body) {
        return """
            package app;
            @jakarta.inject.Singleton
            public class Routes implements io.micronaut.web.router.builder.HttpRoutes {
                @Override
                public void routes(io.micronaut.web.router.builder.HttpRouteBuilder routes) {
                    routes.GET("%s", (request, variables) -> io.micronaut.http.HttpResponse.ok("%s").contentType("text/plain"));
                }
            }
            """.formatted(path, body);
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
