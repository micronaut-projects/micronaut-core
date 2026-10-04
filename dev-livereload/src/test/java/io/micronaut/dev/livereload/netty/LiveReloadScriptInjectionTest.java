package io.micronaut.dev.livereload.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.dev.DevRuntime;
import io.micronaut.dev.MicronautDevMain;
import io.micronaut.dev.livereload.LiveReloadServer;
import io.micronaut.dev.manifest.DevManifest;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveReloadScriptInjectionTest {

    private static final String PAGE = "<html><body><p>hi</p></body></html>";

    @TempDir
    Path project;

    @Test
    void theServedContentLengthIsThatOfThePageWithTheScriptAndAnEncodedPageIsLeftAlone() throws Exception {
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
        Files.writeString(src.resolve("Pages.java"), """
            package app;
            import io.micronaut.http.HttpResponse;
            import io.micronaut.http.MediaType;
            import io.micronaut.http.annotation.Controller;
            import io.micronaut.http.annotation.Get;
            import java.nio.charset.StandardCharsets;
            @Controller
            public class Pages {
                static final String PAGE = "%s";
                @Get(value = "/text", produces = MediaType.TEXT_HTML)
                public HttpResponse<String> text() {
                    return HttpResponse.ok(PAGE).contentType(MediaType.TEXT_HTML_TYPE).contentLength(PAGE.getBytes(StandardCharsets.UTF_8).length);
                }
                @Get(value = "/bytes", produces = MediaType.TEXT_HTML)
                public HttpResponse<byte[]> bytes() {
                    byte[] page = PAGE.getBytes(StandardCharsets.UTF_8);
                    return HttpResponse.ok(page).contentType(MediaType.TEXT_HTML_TYPE).contentLength(page.length);
                }
                @Get(value = "/gzip", produces = MediaType.TEXT_HTML)
                public HttpResponse<byte[]> gzip() throws java.io.IOException {
                    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                    try (java.util.zip.GZIPOutputStream zip = new java.util.zip.GZIPOutputStream(out)) {
                        zip.write(PAGE.getBytes(StandardCharsets.UTF_8));
                    }
                    byte[] page = out.toByteArray();
                    return HttpResponse.ok(page).contentType(MediaType.TEXT_HTML_TYPE).header("Content-Encoding", "gzip").contentLength(page.length);
                }
            }
            """.formatted(PAGE.replace("\"", "\\\"")));
        Path site = Files.createDirectories(project.resolve("src/main/resources/static/site"));
        Files.writeString(site.resolve("index.html"), PAGE);
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

        DevRuntime runtime = new MicronautDevMain().launch(DevManifest.load(manifestFile), new String[0]);
        try {
            ApplicationContext context = runtime.context().orElseThrow();
            EmbeddedServer server = context.getBean(EmbeddedServer.class);
            long deadline = System.nanoTime() + java.time.Duration.ofSeconds(30).toNanos();
            while (!server.isRunning() && System.nanoTime() < deadline) {
                Thread.sleep(20);
            }
            int port = server.getPort();
            int liveReloadPort = runtime.liveReload().map(LiveReloadServer::port).orElseThrow();
            String expected = PAGE.replace("</body>", LiveReloadServer.scriptTag(liveReloadPort) + "</body>");
            HttpClient client = HttpClient.newHttpClient();
            for (String path : List.of("/text", "/bytes", "/site/index.html")) {
                HttpResponse<byte[]> response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(), HttpResponse.BodyHandlers.ofByteArray());
                assertEquals(200, response.statusCode(), path);
                assertEquals(expected, new String(response.body(), StandardCharsets.UTF_8), path);
                // the length the page was served with is that of the page with the script, not the one the controller set
                assertEquals(response.body().length, Integer.parseInt(response.headers().firstValue("content-length").orElseThrow()), path);
                assertFalse(response.headers().firstValue("transfer-encoding").isPresent(), path);
            }

            // a compressed page cannot take the script: it is served as it was encoded
            HttpResponse<byte[]> gzip = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/gzip")).build(), HttpResponse.BodyHandlers.ofByteArray());
            assertEquals("gzip", gzip.headers().firstValue("content-encoding").orElse(null));
            assertEquals(gzip.body().length, Integer.parseInt(gzip.headers().firstValue("content-length").orElseThrow()));
            byte[] page;
            try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(gzip.body()))) {
                page = in.readAllBytes();
            }
            assertArrayEquals(PAGE.getBytes(StandardCharsets.UTF_8), page);
            assertTrue(gzip.headers().firstValue("cache-control").map(value -> !value.contains("no-store")).orElse(true));
        } finally {
            runtime.close();
        }
    }
}
