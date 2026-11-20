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
import java.net.URI;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.http.HttpHeaders;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pre-compressed siblings of static resources, of the configured static resources and of
 * {@link StaticResources#preCompressed(boolean)}, for the classpath and the file system. The
 * requests are sent by a plain client, which does not decode the content.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class PreCompressedStaticResourcesTest {
    public static final String SPEC_NAME = "PreCompressedStaticResourcesTest";
    private static final String ROOT_PROPERTY = "pre-compressed-static-resources.root";
    private static final String IDENTITY = "console.log('identity');";

    /**
     * The prefixes of the same resources: configured and functional, of the classpath and of the file system.
     */
    private static final List<String> PREFIXES = List.of("/cfg-cp", "/cfg-files", "/fn-cp", "/fn-files");

    @TempDir
    Path tempDir;

    private Path root;

    @BeforeEach
    void createFiles() throws IOException {
        root = Files.createDirectories(tempDir.resolve("root"));
        Files.writeString(root.resolve("app.js"), IDENTITY);
        Files.writeString(root.resolve("app.js.br"), "br-bytes");
        Files.writeString(root.resolve("app.js.gz"), "gz-bytes");
        Files.writeString(root.resolve("plain.txt"), "plain");
        Files.writeString(root.resolve("index.html"), "<html>index</html>");
        Files.writeString(root.resolve("index.html.gz"), "gz-index");
        Files.writeString(tempDir.resolve("outside.js.gz"), "outside");
    }

    @Test
    void theEncodingIsNegotiated() throws Exception {
        // Accept-Encoding -> expected Content-Encoding, or null for the resource itself
        Object[][] matrix = {
            {null, null},
            {"br", "br"},
            {"gzip", "gzip"},
            {"x-gzip", "gzip"},
            {"gzip, br", "br"},
            {"br;q=0.5, gzip", "gzip"},
            {"br;q=0, gzip;q=0.1", "gzip"},
            {"*", "br"},
            {"*;q=0.5, br;q=0", "gzip"},
            // no zst sibling: the next acceptable coding
            {"zstd, gzip;q=0.5", "gzip"},
            {"zstd", null},
            {"deflate", null},
            {"identity", null},
            {"br;q=0.5, identity", null},
            {"gzip, identity;q=0", "gzip"},
            {"br;q=0, gzip;q=0", null},
            {"GZIP", "gzip"},
            {"br;q=bad", null},
        };
        try (ServerUnderTest server = server()) {
            for (String prefix : PREFIXES) {
                for (Object[] row : matrix) {
                    String acceptEncoding = (String) row[0];
                    String expected = (String) row[1];
                    Response response = get(server, prefix + "/app.js", acceptEncoding == null ? Map.of() : Map.of("Accept-Encoding", acceptEncoding));
                    String context = prefix + " " + acceptEncoding;
                    assertEquals(200, response.statusCode(), context);
                    assertEquals(expected, response.headers().firstValue("Content-Encoding").orElse(null), context);
                    String body = expected == null ? IDENTITY : expected.charAt(0) == 'b' ? "br-bytes" : "gz-bytes";
                    assertEquals(body, response.body(), context);
                    assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/javascript")
                        || response.headers().firstValue("Content-Type").orElse("").startsWith("text/javascript"), context);
                    assertTrue(response.headers().allValues("Vary").stream().anyMatch(v -> v.toLowerCase(Locale.ROOT).contains("accept-encoding")), context);
                    assertEquals(String.valueOf(body.length()), response.headers().firstValue("Content-Length").orElse(null), context);
                }
            }
        }
    }

    @Test
    void theEntityTagsDifferPerEncodingAndAreValidated() throws Exception {
        try (ServerUnderTest server = server()) {
            for (String prefix : PREFIXES) {
                Set<String> tags = new HashSet<>();
                for (String encoding : new String[]{"identity", "br", "gzip"}) {
                    Response response = get(server, prefix + "/app.js", Map.of("Accept-Encoding", encoding));
                    String tag = response.headers().firstValue("ETag").orElse(null);
                    assertNotNull(tag, prefix + " " + encoding);
                    assertTrue(tags.add(tag), prefix + " " + encoding);

                    Response notModified = get(server, prefix + "/app.js", Map.of("Accept-Encoding", encoding, "If-None-Match", "\"other\", " + tag));
                    assertEquals(304, notModified.statusCode(), prefix + " " + encoding);
                    assertEquals("", notModified.body());
                    assertEquals(tag, notModified.headers().firstValue("ETag").orElse(null), prefix + " " + encoding);
                }
                // the tag of another encoding is another representation
                Response gzipTagForBr = get(server, prefix + "/app.js", Map.of("Accept-Encoding", "gzip"));
                String gzipTag = gzipTagForBr.headers().firstValue("ETag").orElseThrow();
                Response modified = get(server, prefix + "/app.js", Map.of("Accept-Encoding", "br", "If-None-Match", gzipTag));
                assertEquals(200, modified.statusCode(), prefix);
                assertEquals("br-bytes", modified.body(), prefix);
                // If-None-Match takes precedence over If-Modified-Since
                String lastModified = modified.headers().firstValue("Last-Modified").orElseThrow();
                Response both = get(server, prefix + "/app.js", Map.of("Accept-Encoding", "br", "If-None-Match", gzipTag, "If-Modified-Since", lastModified));
                assertEquals(200, both.statusCode(), prefix);
            }
        }
    }

    @Test
    void aRangeIsARangeOfTheResourceItself() throws Exception {
        try (ServerUnderTest server = server()) {
            for (String prefix : List.of("/cfg-files", "/fn-files")) {
                Response partial = get(server, prefix + "/app.js", Map.of("Accept-Encoding", "br, gzip", "Range", "bytes=0-6"));
                assertEquals(206, partial.statusCode(), prefix);
                assertEquals("console", partial.body(), prefix);
                assertFalse(partial.headers().firstValue("Content-Encoding").isPresent(), prefix);
            }
            for (String prefix : List.of("/cfg-cp", "/fn-cp")) {
                // no ranges of a resource in a jar, but never a compressed one
                Response response = get(server, prefix + "/app.js", Map.of("Accept-Encoding", "br, gzip", "Range", "bytes=0-6"));
                assertFalse(response.headers().firstValue("Content-Encoding").isPresent(), prefix);
            }
        }
    }

    @Test
    void aResourceWithoutSiblingIsServedAsItIs() throws Exception {
        try (ServerUnderTest server = server()) {
            for (String prefix : PREFIXES) {
                Response response = get(server, prefix + "/plain.txt", Map.of("Accept-Encoding", "br, gzip, zstd"));
                assertEquals(200, response.statusCode(), prefix);
                assertEquals("plain", response.body(), prefix);
                assertFalse(response.headers().firstValue("Content-Encoding").isPresent(), prefix);
                // a sibling alone is not a resource of the original path
                assertEquals(404, get(server, prefix + "/missing.js", Map.of("Accept-Encoding", "gzip")).statusCode(), prefix);
            }
        }
    }

    @Test
    void theIndexPageHasItsSibling() throws Exception {
        try (ServerUnderTest server = server()) {
            for (String prefix : PREFIXES) {
                Response response = get(server, prefix + "/", Map.of("Accept-Encoding", "gzip"));
                assertEquals(200, response.statusCode(), prefix);
                assertEquals("gzip", response.headers().firstValue("Content-Encoding").orElse(null), prefix);
                assertEquals("gz-index", response.body(), prefix);
                assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("text/html"), prefix);
            }
        }
    }

    @Test
    void aSiblingDoesNotLeaveTheBase() throws Exception {
        try (ServerUnderTest server = server()) {
            for (String prefix : List.of("/cfg-files", "/fn-files")) {
                for (String path : new String[]{"/%2e%2e/outside.js", "/..%2foutside.js", "/sub/%2e%2e/%2e%2e/outside.js"}) {
                    Response response = get(server, prefix + path, Map.of("Accept-Encoding", "gzip"));
                    assertNotEquals("outside", response.body(), prefix + path);
                    assertNotEquals(200, response.statusCode(), prefix + path);
                }
            }
        }
    }

    @Test
    void theSiblingsAreNotServedUnlessEnabled() throws Exception {
        try (ServerUnderTest server = server()) {
            for (String prefix : List.of("/cfg-off", "/fn-off")) {
                Response response = get(server, prefix + "/app.js", Map.of("Accept-Encoding", "br"));
                assertEquals(200, response.statusCode(), prefix);
                assertFalse(response.headers().firstValue("ETag").isPresent(), prefix);
                // possibly compressed on the fly, but never the sibling
                assertNotEquals("br-bytes", response.body(), prefix);
            }
        }
    }

    @Test
    void aHeadRequestHasTheHeadersOfTheSibling() throws Exception {
        try (ServerUnderTest server = server()) {
            for (String prefix : PREFIXES) {
                Response response = send(server, prefix + "/app.js", "HEAD", Map.of("Accept-Encoding", "br"));
                assertEquals(200, response.statusCode(), prefix);
                assertEquals("br", response.headers().firstValue("Content-Encoding").orElse(null), prefix);
                assertEquals("", response.body(), prefix);
            }
        }
    }

    private ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME, Map.of(
            ROOT_PROPERTY, root.toString(),
            "micronaut.server.netty.compression-threshold", Integer.MAX_VALUE,
            "micronaut.router.static-resources.cfg-cp.mapping", "/cfg-cp/**",
            "micronaut.router.static-resources.cfg-cp.paths", "classpath:precompressed",
            "micronaut.router.static-resources.cfg-cp.pre-compressed", true,
            "micronaut.router.static-resources.cfg-files.mapping", "/cfg-files/**",
            "micronaut.router.static-resources.cfg-files.paths", "file:" + root,
            "micronaut.router.static-resources.cfg-files.pre-compressed", true,
            "micronaut.router.static-resources.cfg-off.mapping", "/cfg-off/**",
            "micronaut.router.static-resources.cfg-off.paths", "classpath:precompressed"
        ));
    }

    private static Response get(ServerUnderTest server, String path, Map<String, String> headers) throws IOException, GeneralSecurityException {
        return send(server, path, "GET", headers);
    }

    /**
     * Sends a request with a plain {@link HttpURLConnection}, which does not decode the content,
     * over TLS when the server uses it, trusting its test certificate.
     */
    private static Response send(ServerUnderTest server, String path, String method, Map<String, String> headers) throws IOException, GeneralSecurityException {
        URL base = server.getURL().orElseThrow();
        URL url = URI.create(base.getProtocol() + "://" + base.getHost() + ":" + base.getPort() + path).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        if (connection instanceof HttpsURLConnection https) {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[]{new TrustAll()}, null);
            https.setSSLSocketFactory(context.getSocketFactory());
            https.setHostnameVerifier((host, session) -> true);
        }
        connection.setRequestMethod(method);
        connection.setUseCaches(false);
        headers.forEach(connection::setRequestProperty);
        try {
            int status = connection.getResponseCode();
            Map<String, List<String>> responseHeaders = new HashMap<>();
            connection.getHeaderFields().forEach((name, values) -> {
                if (name != null) {
                    responseHeaders.put(name, values);
                }
            });
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String body = "";
            if (stream != null) {
                try (stream) {
                    body = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
            return new Response(status, HttpHeaders.of(responseHeaders, (name, value) -> true), body);
        } finally {
            connection.disconnect();
        }
    }

    private record Response(int statusCode, HttpHeaders headers, String body) {
    }

    @SuppressWarnings("java:S4830") // the self-signed certificate of the test server
    private static final class TrustAll implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
            // trusted
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
            // trusted
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class PreCompressedRoutes implements HttpRoutes {

        private final Path root;

        PreCompressedRoutes(@Value("${" + ROOT_PROPERTY + "}") String root) {
            this.root = Path.of(root);
        }

        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.GET("/fn-cp").resources(StaticResources.classpath("precompressed").preCompressed(true));
            routes.GET("/fn-files").resources(StaticResources.fileSystem(root).preCompressed(true));
            routes.GET("/fn-off").resources(StaticResources.classpath("precompressed"));
        }
    }
}
