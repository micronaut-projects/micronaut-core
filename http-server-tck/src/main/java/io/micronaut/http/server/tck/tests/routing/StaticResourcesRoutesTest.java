/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.server.tck.tests.routing;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.server.routes.StaticResources;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Static resources served by handler routes: {@link HttpRouteBuilder#resources} with
 * {@link StaticResources} of the classpath and of a directory of the file system.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class StaticResourcesRoutesTest {
    public static final String SPEC_NAME = "StaticResourcesRoutesTest";
    private static final String ROOT_PROPERTY = "static-resources-routes.root";
    private static final String CACHE_CONTROL = "public, max-age=31536000, immutable";
    private static final String DATA = "0123456789abcdefghijklmnopqrstuvwxyz";

    @TempDir
    Path tempDir;

    private Path root;

    @BeforeEach
    void createFiles() throws IOException {
        root = Files.createDirectories(tempDir.resolve("root"));
        Files.writeString(root.resolve("hello.txt"), "Hello file");
        Files.writeString(root.resolve("data.txt"), DATA);
        Files.writeString(root.resolve("index.html"), "<html><body>file index</body></html>");
        Files.createDirectories(root.resolve("sub"));
        Files.writeString(root.resolve("sub/page.html"), "<html><body>sub page</body></html>");
        Files.writeString(tempDir.resolve("secret.txt"), "secret");
    }

    @Test
    void aClasspathResourceIsServedWithTheContentTypeOfItsName() throws IOException {
        try (ServerUnderTest server = server()) {
            HttpResponse<String> text = exchange(server, HttpRequest.GET("/static-fn/hello.txt"));
            assertEquals(HttpStatus.OK, text.getStatus());
            assertEquals("Hello static", text.body());
            assertEquals(MediaType.TEXT_PLAIN, mediaType(text));

            HttpResponse<String> css = exchange(server, HttpRequest.GET("/static-fn/css/site.css").accept(MediaType.ALL_TYPE));
            assertEquals(HttpStatus.OK, css.getStatus());
            assertEquals("body { color: red; }", css.body());
            assertEquals("text/css", mediaType(css));
        }
    }

    @Test
    void aFileOfTheFileSystemIsServedWithTheContentTypeOfItsName() throws IOException {
        try (ServerUnderTest server = server()) {
            HttpResponse<String> text = exchange(server, HttpRequest.GET("/files/hello.txt"));
            assertEquals(HttpStatus.OK, text.getStatus());
            assertEquals("Hello file", text.body());
            assertEquals(MediaType.TEXT_PLAIN, mediaType(text));

            HttpResponse<String> page = exchange(server, HttpRequest.GET("/files/sub/page.html").accept(MediaType.TEXT_HTML));
            assertEquals(HttpStatus.OK, page.getStatus());
            assertEquals("<html><body>sub page</body></html>", page.body());
            assertEquals(MediaType.TEXT_HTML, mediaType(page));
        }
    }

    @Test
    void thePrefixAndADirectoryAreServedTheirIndexFile() throws IOException {
        try (ServerUnderTest server = server()) {
            for (String uri : new String[]{"/static-fn", "/static-fn/"}) {
                HttpResponse<String> index = exchange(server, HttpRequest.GET(uri).accept(MediaType.TEXT_HTML));
                assertEquals(HttpStatus.OK, index.getStatus(), uri);
                assertEquals("<html><body>static index</body></html>", index.body(), uri);
                assertEquals(MediaType.TEXT_HTML, mediaType(index), uri);
            }
            assertEquals("<html><body>docs index</body></html>", exchange(server, HttpRequest.GET("/static-fn/docs")).body());
            assertEquals("<html><body>file index</body></html>", exchange(server, HttpRequest.GET("/files")).body());
        }
    }

    @Test
    void aMissingResourceAndADirectoryWithoutIndexAreNotFound() throws IOException {
        try (ServerUnderTest server = server()) {
            for (String uri : new String[]{
                "/static-fn/missing.txt",
                "/static-fn/css/missing.css",
                // a directory without an index file
                "/static-fn/nodoc",
                "/files/missing.txt",
                "/files/sub",
                // without an index file
                "/raw/docs",
                "/raw/"
            }) {
                assertEquals(HttpStatus.NOT_FOUND, exchange(server, HttpRequest.GET(uri)).getStatus(), uri);
            }
            // the variable of the route
            assertEquals("Hello static", exchange(server, HttpRequest.GET("/raw/hello.txt")).body());
        }
    }

    @Test
    void aPathThatLeavesTheBaseIsNotFound() throws IOException {
        try (ServerUnderTest server = server()) {
            for (String uri : new String[]{
                // the sibling assets/hello.txt of the classpath and secret.txt of the file system
                "/static-fn/%2e%2e/assets/hello.txt",
                "/static-fn/%2E%2E/assets/hello.txt",
                "/static-fn/..%2fassets%2fhello.txt",
                "/static-fn/..%5cassets%5chello.txt",
                "/static-fn/%252e%252e/assets/hello.txt",
                "/static-fn/css/%2e%2e/%2e%2e/assets/hello.txt",
                "/static-fn/%2e/hello.txt",
                "/files/%2e%2e/secret.txt",
                "/files/..%2fsecret.txt",
                "/files/..%5csecret.txt",
                "/files/sub/%2e%2e/%2e%2e/secret.txt",
                // absolute
                "/files/" + tempDir.resolve("secret.txt").toUri().getRawPath().replace("/", "%2f"),
                "/files/C:%5csecret.txt"
            }) {
                HttpResponse<String> response = exchange(server, HttpRequest.GET(uri));
                assertEquals(HttpStatus.NOT_FOUND, response.getStatus(), uri);
                String body = response.getBody(String.class).orElse("");
                assertTrue(!body.contains("secret") && !body.contains("Hello World"), uri + ": " + body);
            }
        }
    }

    @Test
    void aSymbolicLinkOutOfTheDirectoryIsNotFound() throws IOException {
        try {
            Files.createSymbolicLink(root.resolve("link.txt"), tempDir.resolve("secret.txt"));
        } catch (UnsupportedOperationException | IOException e) {
            // the file system has no symbolic links
            return;
        }
        try (ServerUnderTest server = server()) {
            assertEquals(HttpStatus.NOT_FOUND, exchange(server, HttpRequest.GET("/files/link.txt")).getStatus());
        }
    }

    @Test
    void aHeadRequestHasTheHeadersWithoutTheBody() throws IOException {
        try (ServerUnderTest server = server()) {
            for (String uri : new String[]{"/static-fn/hello.txt", "/files/hello.txt"}) {
                HttpResponse<String> head = exchange(server, HttpRequest.HEAD(uri));
                assertEquals(HttpStatus.OK, head.getStatus(), uri);
                assertEquals(MediaType.TEXT_PLAIN, mediaType(head), uri);
                assertTrue(head.getBody(String.class).orElse("").isEmpty(), uri);
            }
            HttpResponse<String> file = exchange(server, HttpRequest.HEAD("/files/hello.txt"));
            assertEquals(String.valueOf("Hello file".length()), file.getHeaders().get(HttpHeaders.CONTENT_LENGTH));
            assertTrue(file.getHeaders().contains(HttpHeaders.LAST_MODIFIED));
            assertEquals(CACHE_CONTROL, exchange(server, HttpRequest.HEAD("/static-fn/hello.txt")).getHeaders().get(HttpHeaders.CACHE_CONTROL));
            assertEquals(HttpStatus.NOT_FOUND, exchange(server, HttpRequest.HEAD("/files/missing.txt")).getStatus());
        }
    }

    @Test
    void aFileNotModifiedSinceTheRequestedDateIsNotModified() throws IOException {
        try (ServerUnderTest server = server()) {
            HttpResponse<String> first = exchange(server, HttpRequest.GET("/files/hello.txt"));
            assertEquals(HttpStatus.OK, first.getStatus());
            String lastModified = first.getHeaders().get(HttpHeaders.LAST_MODIFIED);
            assertEquals(Files.getLastModifiedTime(root.resolve("hello.txt")).toInstant().getEpochSecond(),
                ZonedDateTime.parse(lastModified, DateTimeFormatter.RFC_1123_DATE_TIME).toEpochSecond());

            HttpResponse<String> notModified = exchange(server, HttpRequest.GET("/files/hello.txt")
                .header(HttpHeaders.IF_MODIFIED_SINCE, lastModified));
            assertEquals(HttpStatus.NOT_MODIFIED, notModified.getStatus());
            assertTrue(notModified.getBody(String.class).orElse("").isEmpty());

            String earlier = DateTimeFormatter.RFC_1123_DATE_TIME.format(
                ZonedDateTime.parse(lastModified, DateTimeFormatter.RFC_1123_DATE_TIME).minusDays(1));
            HttpResponse<String> modified = exchange(server, HttpRequest.GET("/files/hello.txt")
                .header(HttpHeaders.IF_MODIFIED_SINCE, earlier));
            assertEquals(HttpStatus.OK, modified.getStatus());
            assertEquals("Hello file", modified.body());
        }
    }

    @Test
    void aRangeOfAFileIsPartialContent() throws IOException {
        try (ServerUnderTest server = server()) {
            HttpResponse<String> partial = exchange(server, HttpRequest.GET("/files/data.txt").header(HttpHeaders.RANGE, "bytes=10-15"));
            assertEquals(HttpStatus.PARTIAL_CONTENT, partial.getStatus());
            assertEquals("abcdef", partial.body());
            assertEquals("bytes 10-15/" + DATA.length(), partial.getHeaders().get(HttpHeaders.CONTENT_RANGE));
            assertEquals("bytes", exchange(server, HttpRequest.GET("/files/data.txt")).getHeaders().get(HttpHeaders.ACCEPT_RANGES));
        }
    }

    @Test
    void theConfiguredCacheControlHeaderReplacesTheDefaultOne() throws IOException {
        try (ServerUnderTest server = server()) {
            HttpResponse<String> configured = exchange(server, HttpRequest.GET("/static-fn/hello.txt"));
            assertEquals(CACHE_CONTROL, configured.getHeaders().get(HttpHeaders.CACHE_CONTROL));
            // the default header of the files of the server
            String defaultHeader = exchange(server, HttpRequest.GET("/files/hello.txt")).getHeaders().get(HttpHeaders.CACHE_CONTROL);
            assertTrue(defaultHeader != null && !defaultHeader.equals(CACHE_CONTROL), defaultHeader);
        }
    }

    @Test
    void theResourcesOfAGroupHaveTheFiltersOfTheGroup() throws IOException {
        try (ServerUnderTest server = server()) {
            for (String uri : new String[]{"/grouped/assets/hello.txt", "/grouped/assets"}) {
                HttpResponse<String> response = exchange(server, HttpRequest.GET(uri));
                assertEquals(HttpStatus.OK, response.getStatus(), uri);
                assertEquals("true", response.getHeaders().get("X-Group"), uri);
            }
            assertEquals("Hello static", exchange(server, HttpRequest.GET("/grouped/assets/hello.txt")).body());
            // not found by the route of the group: its filters still apply
            HttpResponse<String> missing = exchange(server, HttpRequest.GET("/grouped/assets/missing.txt"));
            assertEquals(HttpStatus.NOT_FOUND, missing.getStatus());
            assertEquals("true", missing.getHeaders().get("X-Group"));
            // the resources outside the group do not have them
            assertNull(exchange(server, HttpRequest.GET("/static-fn/hello.txt")).getHeaders().get("X-Group"));
        }
    }

    @Test
    void theMoreSpecificRoutesUnderThePrefixTakePrecedence() throws IOException {
        try (ServerUnderTest server = server()) {
            assertEquals("special handler", exchange(server, HttpRequest.GET("/static-fn/special.txt")).body());
            assertEquals("controller docs/intro", exchange(server, HttpRequest.GET("/static-fn/controller/docs/intro")).body());
            assertEquals("Hello static", exchange(server, HttpRequest.GET("/static-fn/hello.txt")).body());
        }
    }

    private ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME, Map.of(ROOT_PROPERTY, root.toString()));
    }

    private static HttpResponse<String> exchange(ServerUnderTest server, HttpRequest<?> request) {
        try {
            return server.exchange(request, String.class);
        } catch (HttpClientResponseException e) {
            @SuppressWarnings("unchecked")
            HttpResponse<String> response = (HttpResponse<String>) e.getResponse();
            return response;
        }
    }

    private static String mediaType(HttpResponse<?> response) {
        String contentType = response.getHeaders().get(HttpHeaders.CONTENT_TYPE);
        if (contentType == null) {
            return null;
        }
        int parameters = contentType.indexOf(';');
        return parameters == -1 ? contentType : contentType.substring(0, parameters).strip();
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class StaticRoutes implements HttpRoutes {

        private final Path root;

        StaticRoutes(@Value("${" + ROOT_PROPERTY + "}") String root) {
            this.root = Path.of(root);
        }

        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.resources("/static-fn", StaticResources.classpath("static-fn").cacheControl(CACHE_CONTROL));
            routes.resources("/files", StaticResources.fileSystem(root));
            routes.GET("/raw/{+file}", StaticResources.classpath("static-fn").pathVariable("file").indexFile(null));
            routes.GET("/static-fn/special.txt", (request, pathVariables) -> HttpResponse.ok("special handler").contentType(MediaType.TEXT_PLAIN_TYPE));
            routes.path("/grouped", group -> {
                group.after((request, response) -> response.header("X-Group", "true"));
                group.resources("/assets", StaticResources.of("classpath:static-fn"));
            });
        }
    }

    @Controller("/static-fn/controller")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class SpecificController {
        @Get("/docs/{name}")
        @Produces(MediaType.TEXT_PLAIN)
        String docs(String name) {
            return "controller docs/" + name;
        }
    }
}
