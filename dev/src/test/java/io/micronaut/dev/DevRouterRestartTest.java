package io.micronaut.dev;

import io.micronaut.context.ApplicationContext;
import io.micronaut.dev.manifest.DevManifest;
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
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every kind of route reloads under {@link io.micronaut.context.reload.ReloadStrategy#RESTART}: the next generation
 * serves the routes its classes declare, and a route the edit removed answers 404, whether the route comes from a
 * controller, from an {@link io.micronaut.web.router.builder.HttpRoutes} bean, a server filter, or a status or error
 * route.
 */
class DevRouterRestartTest {

    @TempDir
    Path project;

    private int port;
    private Path src;
    private DevRuntime runtime;
    private int generation;

    @BeforeEach
    void setUp() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
    }

    @Test
    void controllerRoutesAreAddedMovedAndRemoved() throws Exception {
        launch("HelloController.java", controller("""
            @io.micronaut.http.annotation.Get(value = "/hello", produces = "text/plain")
            public String hello() { return "hello"; }
            """));
        try {
            assertEquals("200 hello", get("/hello"));
            assertStatus(404, "/added");

            // a method added
            edit("HelloController.java", controller("""
                @io.micronaut.http.annotation.Get(value = "/hello", produces = "text/plain")
                public String hello() { return "hello"; }
                @io.micronaut.http.annotation.Get(value = "/added", produces = "text/plain")
                public String added() { return "added"; }
                """));
            assertEquals("200 hello", get("/hello"));
            assertEquals("200 added", get("/added"));

            // a path changed: the old one is gone
            edit("HelloController.java", controller("""
                @io.micronaut.http.annotation.Get(value = "/hi", produces = "text/plain")
                public String hello() { return "hi"; }
                @io.micronaut.http.annotation.Get(value = "/added", produces = "text/plain")
                public String added() { return "added"; }
                """));
            assertEquals("200 hi", get("/hi"));
            assertStatus(404, "/hello");

            // a method removed
            edit("HelloController.java", controller("""
                @io.micronaut.http.annotation.Get(value = "/hi", produces = "text/plain")
                public String hello() { return "hi"; }
                """));
            assertEquals("200 hi", get("/hi"));
            assertStatus(404, "/added");

            // the whole controller removed
            delete("HelloController.java");
            assertStatus(404, "/hi");
        } finally {
            runtime.close();
        }
    }

    @Test
    void functionalRoutesAreAddedChangedAndRemoved() throws Exception {
        // no HttpRoutes bean at first: the assembly that needs one is not there either
        launch("HelloController.java", controller("""
            @io.micronaut.http.annotation.Get(value = "/hello", produces = "text/plain")
            public String hello() { return "hello"; }
            """));
        try {
            assertEquals("200 hello", get("/hello"));
            assertStatus(404, "/fn");

            // the first HttpRoutes bean
            edit("Routes.java", routes("""
                routes.GET("/fn", (request, variables) -> text("fn-1"));
                """));
            assertEquals("200 fn-1", get("/fn"));
            assertEquals("200 hello", get("/hello"));

            // a route added, and the handler of the other changed
            edit("Routes.java", routes("""
                routes.GET("/fn", (request, variables) -> text("fn-2"));
                routes.GET("/fn/{name}", (request, variables) -> text("fn-" + variables.getString("name")));
                """));
            assertEquals("200 fn-2", get("/fn"));
            assertEquals("200 fn-x", get("/fn/x"));

            // a path changed: the old one is gone
            edit("Routes.java", routes("""
                routes.GET("/moved", (request, variables) -> text("moved"));
                routes.GET("/fn/{name}", (request, variables) -> text("fn-" + variables.getString("name")));
                """));
            assertEquals("200 moved", get("/moved"));
            assertStatus(404, "/fn");

            // the bean removed: none of its routes stays
            delete("Routes.java");
            assertStatus(404, "/moved");
            assertStatus(404, "/fn/x");
            assertEquals("200 hello", get("/hello"));
        } finally {
            runtime.close();
        }
    }

    @Test
    void serverFiltersAndRouteFiltersAreAddedAndRemoved() throws Exception {
        launch("HelloController.java", controller("""
            @io.micronaut.http.annotation.Get(value = "/hello", produces = "text/plain")
            public String hello() { return "hello"; }
            """));
        try {
            assertFalse(header("/hello", "X-Annotated").contains("annotated"));

            // an annotated server filter added
            edit("TagFilter.java", """
                package app;
                @io.micronaut.http.annotation.ServerFilter("/**")
                public class TagFilter {
                    @io.micronaut.http.annotation.ResponseFilter
                    public void tag(io.micronaut.http.MutableHttpResponse<?> response) { response.header("X-Annotated", "annotated"); }
                }
                """);
            assertEquals("annotated", header("/hello", "X-Annotated"));

            // a server filter and a route filter declared in code
            edit("Routes.java", routes("""
                routes.serverFilter("/hello").after((request, response) -> response.header("X-Server-Fn", "server-fn"));
                routes.GET("/fn").after((request, response) -> response.header("X-Route-Fn", "route-fn")).and()
                    .handle((request, variables) -> text("fn"));
                """));
            assertEquals("server-fn", header("/hello", "X-Server-Fn"));
            assertEquals("route-fn", header("/fn", "X-Route-Fn"));
            assertEquals("annotated", header("/fn", "X-Annotated"));

            // the server filter in code moved to another path, the route lost its filter
            edit("Routes.java", routes("""
                routes.serverFilter("/fn").after((request, response) -> response.header("X-Server-Fn", "server-fn"));
                routes.GET("/fn", (request, variables) -> text("fn"));
                """));
            assertEquals("", header("/hello", "X-Server-Fn"));
            assertEquals("server-fn", header("/fn", "X-Server-Fn"));
            assertEquals("", header("/fn", "X-Route-Fn"));

            // the annotated filter removed
            delete("TagFilter.java");
            assertEquals("", header("/hello", "X-Annotated"));
            assertEquals("200 hello", get("/hello"));
        } finally {
            runtime.close();
        }
    }

    @Test
    void statusAndErrorRoutesFollowTheirDeclarations() throws Exception {
        launch("HelloController.java", controller("""
            @io.micronaut.http.annotation.Get(value = "/boom", produces = "text/plain")
            public String boom() { throw new IllegalStateException("boom"); }
            """));
        try {
            assertStatus(404, "/missing");
            assertStatus(500, "/boom");

            // annotated status and error routes
            edit("Errors.java", """
                package app;
                @io.micronaut.http.annotation.Controller
                public class Errors {
                    @io.micronaut.http.annotation.Error(status = io.micronaut.http.HttpStatus.NOT_FOUND, global = true)
                    public io.micronaut.http.HttpResponse<String> notFound() {
                        return io.micronaut.http.HttpResponse.<String>notFound().body("annotated-404").contentType("text/plain");
                    }
                    @io.micronaut.http.annotation.Error(exception = IllegalStateException.class, global = true)
                    public io.micronaut.http.HttpResponse<String> failed(IllegalStateException e) {
                        return io.micronaut.http.HttpResponse.<String>status(io.micronaut.http.HttpStatus.CONFLICT).body("annotated-" + e.getMessage()).contentType("text/plain");
                    }
                }
                """);
            assertEquals("404 annotated-404", get("/missing"));
            assertEquals("409 annotated-boom", get("/boom"));

            // the same routes declared in code instead
            delete("Errors.java");
            edit("Routes.java", routes("""
                routes.status(io.micronaut.http.HttpStatus.NOT_FOUND, request ->
                    io.micronaut.http.HttpResponse.<String>notFound().body("fn-404").contentType("text/plain"));
                routes.error(IllegalStateException.class, (request, e) ->
                    io.micronaut.http.HttpResponse.<String>status(io.micronaut.http.HttpStatus.CONFLICT).body("fn-" + e.getMessage()).contentType("text/plain"));
                """));
            assertEquals("404 fn-404", get("/missing"));
            assertEquals("409 fn-boom", get("/boom"));

            // the error route removed, the status route changed
            edit("Routes.java", routes("""
                routes.status(io.micronaut.http.HttpStatus.NOT_FOUND, request ->
                    io.micronaut.http.HttpResponse.<String>notFound().body("fn-404-again").contentType("text/plain"));
                """));
            assertEquals("404 fn-404-again", get("/missing"));
            assertStatus(500, "/boom");

            // none left
            delete("Routes.java");
            String missing = get("/missing");
            assertTrue(missing.startsWith("404 ") && !missing.contains("fn-404"), missing);
        } finally {
            runtime.close();
        }
    }

    private void launch(String file, String source) throws Exception {
        src = Files.createDirectories(project.resolve("src/main/java/app"));
        Files.writeString(src.resolve("Application.java"), """
            package app;
            public class Application {
                public static void main(String[] args) {
                    io.micronaut.runtime.Micronaut.build(args)
                        .properties(java.util.Map.of("spec.name", "DevRouterRestartTest", "micronaut.server.port", args[0]))
                        .mainClass(Application.class)
                        .start();
                }
            }
            """);
        Files.writeString(src.resolve(file), source);
        Path manifestFile = project.resolve("dev.properties");
        List<String> classpath = List.of(System.getProperty("java.class.path").split(File.pathSeparator));
        Files.write(project.resolve("cp.argfile"), classpath);
        Files.writeString(manifestFile, """
            micronaut.dev.main-class=app.Application
            micronaut.dev.strategy=restart
            micronaut.dev.reloadable=build/classes
            micronaut.dev.compile-classpath=@cp.argfile
            micronaut.dev.processor-path=@cp.argfile
            micronaut.dev.sources.java=src/main/java
            micronaut.dev.compile.java.output=build/classes
            micronaut.dev.patch-in-place=false
            """);
        runtime = new MicronautDevMain().launch(DevManifest.load(manifestFile), new String[] {String.valueOf(port)});
        generation = 1;
        awaitServer();
    }

    private void edit(String file, String source) throws Exception {
        Files.writeString(src.resolve(file), source);
        restart();
    }

    private void delete(String file) throws Exception {
        Files.delete(src.resolve(file));
        restart();
    }

    private void restart() throws Exception {
        runtime.reload();
        generation++;
        ApplicationContext context = runtime.awaitGeneration(generation, Duration.ofMinutes(2));
        assertTrue(context.isRunning());
        assertFalse(runtime.isStartFailed());
        awaitServer();
    }

    private static String controller(String members) {
        return "package app; @io.micronaut.http.annotation.Controller public class HelloController { " + members + " }";
    }

    private static String routes(String body) {
        return """
            package app;
            @jakarta.inject.Singleton
            public class Routes implements io.micronaut.web.router.builder.HttpRoutes {
                @Override
                public void routes(io.micronaut.web.router.builder.HttpRouteBuilder routes) {
            """ + body + """
                }
                static io.micronaut.http.HttpResponse<String> text(String body) {
                    return io.micronaut.http.HttpResponse.ok(body).contentType("text/plain");
                }
            }
            """;
    }

    private void assertStatus(int status, String path) throws IOException {
        String response = get(path);
        assertTrue(response.startsWith(status + " "), path + " answered " + response);
    }

    /**
     * @return The status and the body of one request on its own connection
     */
    private String get(String path) throws IOException {
        String response = rawGet(path);
        String status = response.substring("HTTP/1.1 ".length(), response.indexOf('\r'));
        return status.substring(0, 3) + " " + response.substring(response.indexOf("\r\n\r\n") + 4);
    }

    /**
     * @return The value of a response header, or the empty string
     */
    private String header(String path, String name) throws IOException {
        String response = rawGet(path);
        String head = response.substring(0, response.indexOf("\r\n\r\n"));
        for (String line : head.split("\r\n")) {
            int colon = line.indexOf(':');
            if (colon > 0 && line.substring(0, colon).trim().toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT))) {
                return line.substring(colon + 1).trim();
            }
        }
        return "";
    }

    private String rawGet(String path) throws IOException {
        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(60_000);
            socket.getOutputStream().write(("GET " + path + " HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void awaitServer() throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (true) {
            try {
                if (!rawGet("/").isEmpty()) {
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
