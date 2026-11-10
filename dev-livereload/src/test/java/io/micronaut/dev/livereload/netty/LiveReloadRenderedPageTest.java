package io.micronaut.dev.livereload.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.dev.DevRuntime;
import io.micronaut.dev.MicronautDevMain;
import io.micronaut.dev.livereload.LiveReloadScript;
import io.micronaut.dev.livereload.LiveReloadServer;
import io.micronaut.dev.manifest.DevManifest;
import io.micronaut.http.MediaType;
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
        Files.writeString(src.resolve("Policed.java"), """
            package app;
            public record Policed(String name) {
            }
            """);
        // a writer that declares a policy as it writes, which the script would break
        Files.writeString(src.resolve("PolicedWriter.java"), """
            package app;
            import io.micronaut.core.type.Argument;
            import io.micronaut.core.type.MutableHeaders;
            import io.micronaut.http.MediaType;
            import io.micronaut.http.annotation.Produces;
            import io.micronaut.http.body.MessageBodyWriter;
            @jakarta.inject.Singleton
            @Produces(MediaType.TEXT_HTML)
            public class PolicedWriter implements MessageBodyWriter<Policed> {
                @Override
                public void writeTo(Argument<Policed> type, MediaType mediaType, Policed page, MutableHeaders headers, java.io.OutputStream out) {
                    headers.set("Content-Security-Policy", "script-src 'self'");
                    try {
                        out.write(("<html><body><p>" + page.name() + "</p></body></html>").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    } catch (java.io.IOException e) {
                        throw new io.micronaut.http.codec.CodecException(e.getMessage(), e);
                    }
                }
            }
            """);
        Files.writeString(src.resolve("Latin.java"), """
            package app;
            public record Latin(String name, boolean page) {
            }
            """);
        // a writer that writes ISO-8859-1, declaring the charset in the type it sets as it writes or leaving the client to guess
        Files.writeString(src.resolve("LatinWriter.java"), """
            package app;
            import io.micronaut.core.type.Argument;
            import io.micronaut.core.type.MutableHeaders;
            import io.micronaut.http.MediaType;
            import io.micronaut.http.annotation.Produces;
            import io.micronaut.http.body.MessageBodyWriter;
            @jakarta.inject.Singleton
            @Produces(MediaType.TEXT_HTML)
            public class LatinWriter implements MessageBodyWriter<Latin> {
                @Override
                public void writeTo(Argument<Latin> type, MediaType mediaType, Latin latin, MutableHeaders headers, java.io.OutputStream out) {
                    String html = latin.page() ? "<html><body><p>" + latin.name() + "</p></body></html>" : "<p>" + latin.name() + "</p>";
                    if (latin.page()) {
                        headers.set("Content-Type", "text/html;charset=ISO-8859-1");
                    }
                    try {
                        out.write(html.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
                    } catch (java.io.IOException e) {
                        throw new io.micronaut.http.codec.CodecException(e.getMessage(), e);
                    }
                }
            }
            """);
        Files.writeString(src.resolve("Plain.java"), """
            package app;
            public record Plain(String name) {
            }
            """);
        // a writer that marks what it writes as another type than HTML as it writes
        Files.writeString(src.resolve("PlainWriter.java"), """
            package app;
            import io.micronaut.core.type.Argument;
            import io.micronaut.core.type.MutableHeaders;
            import io.micronaut.http.MediaType;
            import io.micronaut.http.annotation.Produces;
            import io.micronaut.http.body.MessageBodyWriter;
            @jakarta.inject.Singleton
            @Produces(MediaType.TEXT_HTML)
            public class PlainWriter implements MessageBodyWriter<Plain> {
                @Override
                public void writeTo(Argument<Plain> type, MediaType mediaType, Plain plain, MutableHeaders headers, java.io.OutputStream out) {
                    headers.set("Content-Type", "text/plain");
                    try {
                        out.write(("<html><body><p>" + plain.name() + "</p></body></html>").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    } catch (java.io.IOException e) {
                        throw new io.micronaut.http.codec.CodecException(e.getMessage(), e);
                    }
                }
            }
            """);
        Files.writeString(src.resolve("Partial.java"), """
            package app;
            public record Partial(String name) {
            }
            """);
        // a writer that writes only through the server's own response path, and gives up writing to a stream after it
        // changed a header
        Files.writeString(src.resolve("PartialWriter.java"), """
            package app;
            import io.micronaut.core.type.Argument;
            import io.micronaut.core.type.MutableHeaders;
            import io.micronaut.http.HttpRequest;
            import io.micronaut.http.HttpResponse;
            import io.micronaut.http.MediaType;
            import io.micronaut.http.annotation.Produces;
            import io.micronaut.http.body.ByteBodyFactory;
            import io.micronaut.http.body.CloseableByteBody;
            import io.micronaut.http.body.ResponseBodyWriter;
            @jakarta.inject.Singleton
            @Produces(MediaType.TEXT_HTML)
            public class PartialWriter implements ResponseBodyWriter<Partial> {
                @Override
                public void writeTo(Argument<Partial> type, MediaType mediaType, Partial partial, MutableHeaders headers, java.io.OutputStream out) {
                    headers.set("X-Partial", "true");
                    throw new UnsupportedOperationException("written by the server only");
                }
                @Override
                public CloseableByteBody writePiece(ByteBodyFactory bodyFactory, HttpRequest<?> request, HttpResponse<?> response, Argument<Partial> type, MediaType mediaType, Partial partial) {
                    return bodyFactory.copyOf("<html><body><p>" + partial.name() + "</p></body></html>", java.nio.charset.StandardCharsets.UTF_8);
                }
            }
            """);
        // a policy the application adds to every response of a path, through the server's header populator
        Files.writeString(src.resolve("PolicyHeaders.java"), """
            package app;
            @jakarta.inject.Singleton
            public class PolicyHeaders implements io.micronaut.http.server.filter.ResponseHeaderPopulator {
                @Override
                public java.util.Collection<io.micronaut.http.HttpHeaderEntry> findHttpHeaders(io.micronaut.http.HttpRequest<?> request, io.micronaut.http.HttpResponse<?> response) {
                    return request.getPath().equals("/populated") ? java.util.List.of(new io.micronaut.http.HttpHeaderEntry("Content-Security-Policy", "script-src 'self'")) : null;
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
                @Get(value = "/stream", produces = MediaType.TEXT_HTML)
                public java.io.InputStream stream() {
                    return new java.io.ByteArrayInputStream("%s".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                @Get(value = "/populated", produces = MediaType.TEXT_HTML)
                public io.micronaut.http.HttpResponse<String> populated() {
                    return io.micronaut.http.HttpResponse.ok("%s").contentType(MediaType.TEXT_HTML_TYPE);
                }
                @Get(value = "/policed", produces = MediaType.TEXT_HTML)
                public Policed policed() {
                    return new Policed("hi");
                }
                @Get(value = "/latin", produces = MediaType.TEXT_HTML)
                public Latin latin() {
                    return new Latin("caf\u00e9", false);
                }
                @Get(value = "/latin-page", produces = MediaType.TEXT_HTML)
                public Latin latinPage() {
                    return new Latin("caf\u00e9", true);
                }
                @Get(value = "/plain", produces = MediaType.TEXT_HTML)
                public Plain plain() {
                    return new Plain("hi");
                }
                @Get(value = "/partial", produces = MediaType.TEXT_HTML)
                public Partial partial() {
                    return new Partial("hi");
                }
                @Get("/json")
                public java.util.Map<String, String> json() {
                    return java.util.Map.of("page", "%s");
                }
            }
            """.formatted(PAGE.replace("\"", "\\\""), PAGE.replace("\"", "\\\""), PAGE.replace("\"", "\\\""), PAGE.replace("\"", "\\\"")));
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

            // a HEAD request gets the length and cache header of the page with the script, as a GET does
            for (String path : List.of("/writable", "/card", "/latin-page")) {
                HttpResponse<byte[]> get = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(), HttpResponse.BodyHandlers.ofByteArray());
                HttpResponse<Void> head = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).method("HEAD", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding());
                assertEquals(200, head.statusCode(), path);
                assertEquals(String.valueOf(get.body().length), head.headers().firstValue("content-length").orElse(null), path);
                assertEquals(get.headers().firstValue("content-type").orElse(null), head.headers().firstValue("content-type").orElse(null), path);
                assertEquals("no-store", head.headers().firstValue("cache-control").orElse(null), path);
            }

            // a page without a body end is sent as its writer wrote it, with the type it has, which declares no charset
            HttpResponse<byte[]> latin = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/latin")).build(), HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, latin.statusCode());
            assertArrayEquals("<p>caf\u00e9</p>".getBytes(StandardCharsets.ISO_8859_1), latin.body());
            assertEquals("text/html", latin.headers().firstValue("content-type").orElse(null));
            assertEquals(String.valueOf(latin.body().length), latin.headers().firstValue("content-length").orElse(null));
            assertFalse(latin.headers().firstValue("cache-control").map(value -> value.contains("no-store")).orElse(false));
            HttpResponse<Void> latinHead = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/latin")).method("HEAD", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding());
            assertEquals(String.valueOf(latin.body().length), latinHead.headers().firstValue("content-length").orElse(null));

            // a page whose writer declares its charset as it writes is read and sent in that charset
            HttpResponse<byte[]> latinPage = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/latin-page")).build(), HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, latinPage.statusCode());
            assertEquals("<html><body><p>caf\u00e9</p>" + LiveReloadScript.tag(liveReloadPort) + "</body></html>", new String(latinPage.body(), StandardCharsets.ISO_8859_1));
            assertEquals("ISO-8859-1", MediaType.of(latinPage.headers().firstValue("content-type").orElseThrow()).getCharset().orElseThrow().name());
            assertEquals(String.valueOf(latinPage.body().length), latinPage.headers().firstValue("content-length").orElse(null));

            // what a writer marks as another type than HTML as it writes is sent as written
            HttpResponse<String> plain = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/plain")).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, plain.statusCode());
            assertEquals(PAGE, plain.body());
            assertTrue(plain.headers().firstValue("content-type").orElseThrow().startsWith("text/plain"));
            assertFalse(plain.headers().firstValue("cache-control").map(value -> value.contains("no-store")).orElse(false));

            // a writer that gives up writing to a stream leaves the response as it was: the server writes it, without the
            // header the writer set before it gave up
            HttpResponse<String> partial = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/partial")).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, partial.statusCode());
            assertEquals(PAGE, partial.body());
            assertFalse(partial.headers().firstValue("x-partial").isPresent());

            HttpResponse<String> json = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/json")).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, json.statusCode());
            assertTrue(json.headers().firstValue("content-type").orElseThrow().startsWith("application/json"));
            assertFalse(json.body().contains("livereload"), json.body());
            assertFalse(json.headers().firstValue("cache-control").map(value -> value.contains("no-store")).orElse(false));

            // a page whose writer declares a policy is sent as written, as one that declared it before is left alone
            HttpResponse<String> policed = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/policed")).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, policed.statusCode());
            assertEquals(PAGE, policed.body());
            assertEquals("script-src 'self'", policed.headers().firstValue("content-security-policy").orElse(null));
            assertFalse(policed.headers().firstValue("cache-control").map(value -> value.contains("no-store")).orElse(false));

            // the policy the header populator adds is seen too: the page is left alone
            HttpResponse<String> populated = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/populated")).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, populated.statusCode());
            assertEquals(PAGE, populated.body());
            assertEquals("script-src 'self'", populated.headers().firstValue("content-security-policy").orElse(null));

            // a streamed page is written as it comes, by the server
            HttpResponse<String> stream = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/stream")).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, stream.statusCode());
            assertEquals(PAGE, stream.body());

            HttpResponse<String> css = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/site/app.css")).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, css.statusCode());
            assertEquals("/* </body> */ p { color: red; }", css.body());
        }
    }
}
