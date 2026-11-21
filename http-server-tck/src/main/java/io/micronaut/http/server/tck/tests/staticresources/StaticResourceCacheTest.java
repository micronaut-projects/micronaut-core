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
package io.micronaut.http.server.tck.tests.staticresources;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
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
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The in-memory cache of static resources, of the configured static resources and of
 * {@link StaticResources#cache(int, int)}.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class StaticResourceCacheTest {
    public static final String SPEC_NAME = "StaticResourceCacheTest";
    private static final String ROOT_PROPERTY = "static-resource-cache.root";
    private static final List<String> FILE_PREFIXES = List.of("/cfg-files", "/fn-files");

    @TempDir
    Path tempDir;

    private Path root;

    @BeforeEach
    void createFiles() throws IOException {
        root = Files.createDirectories(tempDir.resolve("root"));
        Files.writeString(root.resolve("hello.txt"), "Hello cache");
        Files.writeString(root.resolve("data.txt"), "0123456789abcdefghijklmnopqrstuvwxyz");
        Files.writeString(root.resolve("large.txt"), "x".repeat(2000));
    }

    @Test
    void aCachedFileIsServedWithTheHeadersOfAFile() throws IOException {
        try (ServerUnderTest server = server()) {
            for (String prefix : List.of("/cfg-files", "/fn-files", "/cfg-cp", "/fn-cp")) {
                String path = prefix + (prefix.endsWith("cp") ? "/plain.txt" : "/hello.txt");
                String expected = prefix.endsWith("cp") ? "plain" : "Hello cache";
                for (int i = 0; i < 3; i++) {
                    HttpResponse<String> response = exchange(server, HttpRequest.GET(path));
                    assertEquals(HttpStatus.OK, response.getStatus(), path);
                    assertEquals(expected, response.body(), path);
                    assertTrue(response.getHeaders().get(HttpHeaders.CONTENT_TYPE).startsWith("text/plain"), path);
                    assertTrue(response.getHeaders().contains(HttpHeaders.LAST_MODIFIED), path);
                    assertTrue(response.getHeaders().contains(HttpHeaders.CACHE_CONTROL), path);
                }
            }
        }
    }

    @Test
    void aChangedFileIsLoadedAgain() throws IOException {
        try (ServerUnderTest server = server()) {
            for (String prefix : FILE_PREFIXES) {
                Files.writeString(root.resolve("hello.txt"), "Hello cache");
                assertEquals("Hello cache", exchange(server, HttpRequest.GET(prefix + "/hello.txt")).body(), prefix);
                // the same length, another time
                Files.writeString(root.resolve("hello.txt"), "Hello CACHE");
                Files.setLastModifiedTime(root.resolve("hello.txt"), FileTime.fromMillis(System.currentTimeMillis() + 10_000));
                assertEquals("Hello CACHE", exchange(server, HttpRequest.GET(prefix + "/hello.txt")).body(), prefix);
                // another length
                Files.writeString(root.resolve("hello.txt"), "Hello again, cache");
                assertEquals("Hello again, cache", exchange(server, HttpRequest.GET(prefix + "/hello.txt")).body(), prefix);
                // removed
                Files.delete(root.resolve("hello.txt"));
                assertEquals(HttpStatus.NOT_FOUND, exchange(server, HttpRequest.GET(prefix + "/hello.txt")).getStatus(), prefix);
            }
        }
    }

    @Test
    void aRangeAndAConditionalRequestOfACachedFile() throws IOException {
        try (ServerUnderTest server = server()) {
            for (String prefix : FILE_PREFIXES) {
                HttpResponse<String> full = exchange(server, HttpRequest.GET(prefix + "/data.txt"));
                assertEquals(HttpStatus.OK, full.getStatus(), prefix);
                HttpResponse<String> partial = exchange(server, HttpRequest.GET(prefix + "/data.txt").header(HttpHeaders.RANGE, "bytes=10-15"));
                assertEquals(HttpStatus.PARTIAL_CONTENT, partial.getStatus(), prefix);
                assertEquals("abcdef", partial.body(), prefix);
                HttpResponse<String> notModified = exchange(server, HttpRequest.GET(prefix + "/data.txt")
                    .header(HttpHeaders.IF_MODIFIED_SINCE, full.getHeaders().get(HttpHeaders.LAST_MODIFIED)));
                assertEquals(HttpStatus.NOT_MODIFIED, notModified.getStatus(), prefix);
            }
        }
    }

    @Test
    void aLargeFileIsServedFromTheFile() throws IOException {
        try (ServerUnderTest server = server()) {
            for (String prefix : FILE_PREFIXES) {
                HttpResponse<String> response = exchange(server, HttpRequest.GET(prefix + "/large.txt"));
                assertEquals(HttpStatus.OK, response.getStatus(), prefix);
                assertEquals(2000, response.body().length(), prefix);
            }
        }
    }

    @Test
    void moreFilesThanEntriesAreServed() throws IOException {
        for (int i = 0; i < 10; i++) {
            Files.writeString(root.resolve("f" + i + ".txt"), "file " + i);
        }
        try (ServerUnderTest server = server()) {
            for (String prefix : FILE_PREFIXES) {
                for (int round = 0; round < 2; round++) {
                    for (int i = 0; i < 10; i++) {
                        assertEquals("file " + i, exchange(server, HttpRequest.GET(prefix + "/f" + i + ".txt")).body(), prefix + i);
                    }
                }
            }
        }
    }

    @Test
    void theSizeOfTheCacheIsValidated() {
        StaticResources resources = StaticResources.classpath("precompressed");
        assertThrows(IllegalArgumentException.class, () -> resources.cache(-1, 10));
        assertThrows(IllegalArgumentException.class, () -> resources.cache(10, 0));
        resources.cache(0, 10);
    }

    private ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME, Map.of(
            ROOT_PROPERTY, root.toString(),
            "micronaut.router.static-resources.cfg-cp.mapping", "/cfg-cp/**",
            "micronaut.router.static-resources.cfg-cp.paths", "classpath:precompressed",
            "micronaut.router.static-resources.cfg-cp.cache-max-entries", 4,
            "micronaut.router.static-resources.cfg-files.mapping", "/cfg-files/**",
            "micronaut.router.static-resources.cfg-files.paths", "file:" + root,
            "micronaut.router.static-resources.cfg-files.cache-max-entries", 4,
            "micronaut.router.static-resources.cfg-files.cache-max-entry-size", 1024
        ));
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

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class CacheRoutes implements HttpRoutes {

        private final Path root;

        CacheRoutes(@Value("${" + ROOT_PROPERTY + "}") String root) {
            this.root = Path.of(root);
        }

        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.GET("/fn-cp").resources(StaticResources.classpath("precompressed").cache(4, 1024));
            routes.GET("/fn-files").resources(StaticResources.fileSystem(root).cache(4, 1024));
        }
    }
}
