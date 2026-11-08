package io.micronaut.dev.livereload.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.dev.DevRuntime;
import io.micronaut.dev.MicronautDevMain;
import io.micronaut.dev.livereload.LiveReloadScript;
import io.micronaut.dev.livereload.LiveReloadServer;
import io.micronaut.dev.manifest.DevManifest;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A page rendered by a body writer, as a view is, gets the script: at the time the filter sees the response its body is
 * the object the writer renders, not the page, such as the model and view a view filter swapped in. A response of
 * another type, and a static file that is not a page, are left as they are.
 */
class LiveReloadRenderedPageTest {

    private static final String PAGE = "<html><body><p>hi</p></body></html>";

    @TempDir
    Path project;

    @Test
    void aPageRenderedByABodyWriterGetsTheScriptAndOtherResponsesAreLeftAlone() throws Exception {
        Path src = Files.createDirectories(project.resolve("src/main/java/app"));
        Files.writeString(src.resolve("Application.java"), """
            package app;
            public class Application {
                public static void main(String[] args) {
                    io.micronaut.runtime.Micronaut.build(args)
                        .properties(java.util.Map.of("micronaut.server.port", -1,
                            "micronaut.router.static-resources.site.paths", "classpath:site",
                            "micronaut.router.static-resources.site.mapping", "/site/**"))
                        .mainClass(Application.class)
                        .start();
                }
            }
            """);
        Files.writeString(src.resolve("Greeting.java"), """
            package app;
            public record Greeting(String name) {
            }
            """);
        Files.writeString(src.resolve("Page.java"), """
            package app;
            public record Page(String name) {
            }
            """);
        // renders a page, as views' writer renders a model and view
        Files.writeString(src.resolve("PageWriter.java"), """
            package app;
            import io.micronaut.core.type.Argument;
            import io.micronaut.core.type.MutableHeaders;
            import io.micronaut.http.MediaType;
            import io.micronaut.http.annotation.Produces;
            import io.micronaut.http.body.MessageBodyWriter;
            import io.micronaut.http.codec.CodecException;
            @jakarta.inject.Singleton
            @Produces(MediaType.TEXT_HTML)
            public class PageWriter implements MessageBodyWriter<Page> {
                @Override
                public boolean isBlocking() {
                    return true;
                }
                @Override
                public void writeTo(Argument<Page> type, MediaType mediaType, Page page, MutableHeaders headers, java.io.OutputStream out) {
                    if (Thread.currentThread().getName().toLowerCase(java.util.Locale.ROOT).contains("eventloop")) {
                        // a blocking writer is never run on an event loop
                        throw new CodecException("rendered on " + Thread.currentThread().getName());
                    }
                    try {
                        out.write(("<html><body><p>" + page.name() + "</p></body></html>").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    } catch (java.io.IOException e) {
                        throw new CodecException(e.getMessage(), e);
                    }
                }
            }
            """);
        // swaps a controller's model for the page to render, as views' filter swaps it for a model and view
        Files.writeString(src.resolve("PageFilter.java"), """
            package app;
            import io.micronaut.http.MediaType;
            import io.micronaut.http.MutableHttpResponse;
            import io.micronaut.http.annotation.ResponseFilter;
            import io.micronaut.http.annotation.ServerFilter;
            @ServerFilter("/**")
            public class PageFilter implements io.micronaut.core.order.Ordered {
                // ahead of the filters of the default order, so that its response is handled after theirs
                @Override
                public int getOrder() {
                    return -100;
                }
                @ResponseFilter
                @SuppressWarnings("unchecked")
                public void swap(MutableHttpResponse<?> response) {
                    if (response.body() instanceof Greeting greeting) {
                        ((MutableHttpResponse<Object>) response).body(new Page(greeting.name()));
                        response.contentType(MediaType.TEXT_HTML_TYPE);
                    }
                }
            }
            """);
        Files.writeString(src.resolve("Card.java"), """
            package app;
            public record Card(String name) {
            }
            """);
        // a writer the response carries, which is no bean
        Files.writeString(src.resolve("CardWriter.java"), """
            package app;
            import io.micronaut.core.type.Argument;
            import io.micronaut.core.type.MutableHeaders;
            import io.micronaut.http.MediaType;
            import io.micronaut.http.body.MessageBodyWriter;
            public class CardWriter implements MessageBodyWriter<Card> {
                @Override
                public void writeTo(Argument<Card> type, MediaType mediaType, Card card, MutableHeaders headers, java.io.OutputStream out) {
                    try {
                        out.write(("<html><body><p>" + card.name() + "</p></body></html>").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    } catch (java.io.IOException e) {
                        throw new io.micronaut.http.codec.CodecException(e.getMessage(), e);
                    }
                }
            }
            """);
        Files.writeString(src.resolve("Pages.java"), """
            package app;
            import io.micronaut.core.io.Writable;
            import io.micronaut.http.MediaType;
            import io.micronaut.http.annotation.Controller;
            import io.micronaut.http.annotation.Get;
            @Controller
            public class Pages {
                @Get("/view")
                public Greeting view() {
                    return new Greeting("hi");
                }
                @Get(value = "/writable", produces = MediaType.TEXT_HTML)
                public Writable writable() {
                    return out -> out.write("%s");
                }
                @Get("/card")
                public io.micronaut.http.HttpResponse<Card> card() {
                    io.micronaut.http.MutableHttpResponse<Card> response = io.micronaut.http.HttpResponse.ok(new Card("hi"));
                    response.contentType(MediaType.TEXT_HTML_TYPE);
                    response.bodyWriter(new CardWriter());
                    return response;
                }
                @Get("/json")
                public java.util.Map<String, String> json() {
                    return java.util.Map.of("page", "%s");
                }
            }
            """.formatted(PAGE.replace("\"", "\\\""), PAGE.replace("\"", "\\\"")));
        Path site = Files.createDirectories(project.resolve("src/main/resources/static/site"));
        Files.writeString(site.resolve("app.css"), "/* </body> */ p { color: red; }");
        Files.write(project.resolve("cp.argfile"), List.of(System.getProperty("java.class.path").split(File.pathSeparator)));
        Path manifestFile = project.resolve("dev.properties");
        Files.writeString(manifestFile, """
            micronaut.dev.main-class=app.Application
            micronaut.dev.strategy=restart
            micronaut.dev.reloadable=build/classes
            micronaut.dev.compile-classpath=@cp.argfile
            micronaut.dev.processor-path=@cp.argfile
            micronaut.dev.sources.java=src/main/java
            micronaut.dev.compile.java.output=build/classes
            micronaut.dev.resources.static=src/main/resources/static
            micronaut.dev.livereload.port=0
            """);

        try (DevRuntime runtime = new MicronautDevMain().launch(DevManifest.load(manifestFile), new String[0])) {
            ApplicationContext context = runtime.context().orElseThrow();
            EmbeddedServer server = context.getBean(EmbeddedServer.class);
            long deadline = System.nanoTime() + java.time.Duration.ofSeconds(30).toNanos();
            while (!server.isRunning() && System.nanoTime() < deadline) {
                Thread.sleep(20);
            }
            int port = server.getPort();
            int liveReloadPort = runtime.liveReload().map(LiveReloadServer::port).orElseThrow();
            String expected = PAGE.replace("</body>", LiveReloadScript.tag(liveReloadPort) + "</body>");
            HttpClient client = HttpClient.newHttpClient();
            for (String path : List.of("/view", "/writable", "/card")) {
                HttpResponse<byte[]> response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(), HttpResponse.BodyHandlers.ofByteArray());
                assertEquals(200, response.statusCode(), path);
                assertEquals(expected, new String(response.body(), StandardCharsets.UTF_8), path);
                assertTrue(response.headers().firstValue("content-type").orElseThrow().startsWith("text/html"), path);
                assertEquals(response.body().length, Integer.parseInt(response.headers().firstValue("content-length").orElseThrow()), path);
                assertEquals("no-store", response.headers().firstValue("cache-control").orElse(null), path);
            }

            HttpResponse<String> json = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/json")).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, json.statusCode());
            assertTrue(json.headers().firstValue("content-type").orElseThrow().startsWith("application/json"));
            assertFalse(json.body().contains("livereload"), json.body());
            assertFalse(json.headers().firstValue("cache-control").map(value -> value.contains("no-store")).orElse(false));

            HttpResponse<String> css = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/site/app.css")).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, css.statusCode());
            assertEquals("/* </body> */ p { color: red; }", css.body());
        }
    }
}
