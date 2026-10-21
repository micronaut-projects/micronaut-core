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
package io.micronaut.http.server.tck.tests.raw;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.io.socket.SocketUtils;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.client.AsyncRawHttpClient;
import io.micronaut.http.client.ProxyHttpClient;
import io.micronaut.http.client.ProxyRequestOptions;
import io.micronaut.http.client.RawRequestOptions;
import io.micronaut.http.server.util.ForwardedHeaders;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.http.util.HttpHeadersUtil;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.AsyncRequestHandler;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Handler routes, {@link HttpRouteBuilder#any(String)} of a prefix and of every path under it,
 * that relay every request under the prefix to an upstream server with the
 * {@link AsyncRawHttpClient} or the {@link ProxyHttpClient}: the bodies stream in both
 * directions, the headers and the forwarded headers are relayed, and the errors of the upstream
 * are answered.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HandlerRouteProxyTest {
    public static final String SPEC_NAME = "HandlerRouteProxyTest";

    private static final int CHUNK_SIZE = 8192;
    private static final int LARGE_SIZE = 10 * 1024 * 1024;
    private static final int UPLOAD_SIZE = 16 * 1024 * 1024;
    private static final String UNREACHABLE_PORT = "handler-route-proxy.unreachable-port";

    @Test
    void aLargeStreamedResponseIsRelayedIntact() throws IOException {
        try (ServerUnderTest server = server()) {
            HttpResponse<byte[]> response = server.exchange(HttpRequest.GET("/fn-proxy/stream?size=" + LARGE_SIZE), byte[].class);
            assertEquals(HttpStatus.OK, response.getStatus());
            assertEquals(MediaType.APPLICATION_OCTET_STREAM, response.getContentType().map(MediaType::toString).orElse(null));
            assertArrayEquals(RawProxyTest.content(0, LARGE_SIZE), response.body());
        }
    }

    @Test
    void aLargeStreamedUploadIsRelayed() throws IOException {
        try (ServerUnderTest server = server()) {
            byte[] upload = RawProxyTest.content(0, UPLOAD_SIZE);
            HttpResponse<String> response = server.exchange(
                HttpRequest.PUT("/fn-proxy/upload", upload).contentType(MediaType.APPLICATION_OCTET_STREAM_TYPE),
                String.class
            );
            assertEquals(HttpStatus.OK, response.getStatus());
            assertEquals("PUT " + UPLOAD_SIZE + ":" + RawProxyTest.crc(upload), response.body());
        }
    }

    @Test
    void theProxyHttpClientRelaysAStreamedUpload() throws IOException {
        try (ServerUnderTest server = server()) {
            byte[] upload = RawProxyTest.content(7, UPLOAD_SIZE);
            HttpResponse<String> response = server.exchange(
                HttpRequest.POST("/fn-proxied/upload", upload).contentType(MediaType.APPLICATION_OCTET_STREAM_TYPE),
                String.class
            );
            assertEquals(HttpStatus.OK, response.getStatus());
            assertEquals("POST " + UPLOAD_SIZE + ":" + RawProxyTest.crc(upload), response.body());
        }
    }

    @Test
    void theRestOfThePathTheQueryAndTheHeadersAreRelayed() throws IOException {
        try (ServerUnderTest server = server()) {
            HttpResponse<String> response = server.exchange(
                HttpRequest.GET("/fn-proxy/inspect/a%2Fb/c?q=x%20y&n=1")
                    .header("X-Custom", "one")
                    .header("X-Forwarded-For", "198.51.100.1")
                    .header("Keep-Alive", "timeout=5"),
                String.class
            );
            assertEquals(HttpStatus.OK, response.getStatus());
            Map<String, String> received = lines(response.body());
            assertEquals("/fn-upstream/inspect/a%2Fb/c", received.get("path"), received.toString());
            assertEquals("q=x%20y&n=1", received.get("query"));
            assertEquals("one", received.get("x-custom"));
            // the hop-by-hop headers are removed
            assertEquals("", received.get("keep-alive"));
            // the untrusted X-Forwarded-For of the client is replaced, not appended to
            assertFalse(received.get("x-forwarded-for").contains("198.51.100.1"), received.toString());
            assertFalse(received.get("x-forwarded-for").isEmpty(), received.toString());
            String scheme = server.getURL().orElseThrow().getProtocol();
            assertEquals(scheme, received.get("x-forwarded-proto"));
            assertTrue(received.get("forwarded").contains("proto=" + scheme), received.toString());
            // the Host header is computed from the upstream URI, the inbound one is forwarded
            assertFalse(received.get("x-forwarded-host").isEmpty(), received.toString());
            // the response headers are relayed, without the hop-by-hop ones
            assertEquals("kept", response.getHeaders().get("X-End-To-End"));
            assertFalse(response.getHeaders().contains(HttpHeaders.PROXY_AUTHENTICATE));
        }
    }

    @Test
    void everyMethodIsRelayed() throws IOException {
        try (ServerUnderTest server = server()) {
            HttpResponse<String> delete = server.exchange(HttpRequest.DELETE("/fn-proxy/inspect/items/1"), String.class);
            assertEquals("DELETE", lines(delete.body()).get("method"));
            HttpResponse<String> patch = server.exchange(HttpRequest.PATCH("/fn-proxy/inspect", "{}").contentType(MediaType.APPLICATION_JSON_TYPE), String.class);
            assertEquals("PATCH", lines(patch.body()).get("method"));
            assertEquals("/fn-upstream/inspect", lines(patch.body()).get("path"));
        }
    }

    @Test
    void anErrorStatusOfTheUpstreamIsRelayed() throws IOException {
        try (ServerUnderTest server = server()) {
            var error = assertThrows(io.micronaut.http.client.exceptions.HttpClientResponseException.class,
                () -> server.exchange(HttpRequest.GET("/fn-proxy/unavailable"), String.class));
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatus());
            assertEquals("upstream down", error.getResponse().getBody(String.class).orElse(null));
            assertEquals("30", error.getResponse().getHeaders().get(HttpHeaders.RETRY_AFTER));
        }
    }

    @Test
    void anUnreachableUpstreamIsABadGateway() throws IOException {
        try (ServerUnderTest server = server()) {
            var error = assertThrows(io.micronaut.http.client.exceptions.HttpClientResponseException.class,
                () -> server.exchange(HttpRequest.POST("/fn-unreachable/x", "body").contentType(MediaType.TEXT_PLAIN_TYPE), String.class));
            assertEquals(HttpStatus.BAD_GATEWAY, error.getStatus());
        }
    }

    private static Map<String, String> lines(String body) {
        Map<String, String> received = new TreeMap<>();
        for (String line : body.split("\n")) {
            int separator = line.indexOf('=');
            received.put(line.substring(0, separator), line.substring(separator + 1));
        }
        return received;
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME, Map.of(
            "micronaut.http.client.max-content-length", 64 * 1024 * 1024,
            "micronaut.http.client.read-timeout", "30s",
            "micronaut.server.max-request-size", 128 * 1024 * 1024,
            UNREACHABLE_PORT, SocketUtils.findAvailableTcpPort()
        ));
    }

    /**
     * Route every method of a prefix and of every path under it to a handler that relays the
     * requests: the routes of {@code any(prefix)} and {@code any(prefix + "/{+path}")}, which
     * consume every media type.
     *
     * @param routes  The routes
     * @param prefix  The prefix
     * @param handler The handler
     */
    static void prefix(HttpRouteBuilder routes, String prefix, AsyncRequestHandler handler) {
        routes.any(prefix).consumesAll().handleAsync(handler);
        routes.any(prefix + "/{+path}").consumesAll().handleAsync(handler);
    }

    @Controller("/fn-upstream")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Upstream {

        @Get(value = "/stream", produces = MediaType.APPLICATION_OCTET_STREAM)
        Publisher<byte[]> stream(@QueryValue long size) {
            return Flux.generate(() -> 0L, (offset, sink) -> {
                if (offset >= size) {
                    sink.complete();
                    return offset;
                }
                int n = (int) Math.min(CHUNK_SIZE, size - offset);
                sink.next(RawProxyTest.content(offset, n));
                return offset + n;
            });
        }

        @io.micronaut.http.annotation.Put(value = "/upload", consumes = MediaType.APPLICATION_OCTET_STREAM, produces = MediaType.TEXT_PLAIN)
        Mono<String> put(HttpRequest<?> request, @Body Publisher<byte[]> body) {
            return upload(request, body);
        }

        @Post(value = "/upload", consumes = MediaType.APPLICATION_OCTET_STREAM, produces = MediaType.TEXT_PLAIN)
        Mono<String> upload(HttpRequest<?> request, @Body Publisher<byte[]> body) {
            return Flux.from(body)
                .collect(RawProxyTest.UploadDigest::new, RawProxyTest.UploadDigest::update)
                .map(digest -> request.getMethodName() + " " + digest);
        }

        @Get(value = "/unavailable", produces = MediaType.TEXT_PLAIN)
        HttpResponse<String> unavailable() {
            return HttpResponse.<String>status(HttpStatus.SERVICE_UNAVAILABLE).body("upstream down").header(HttpHeaders.RETRY_AFTER, "30");
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class UpstreamInspectRoutes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            // every method and path under /fn-upstream/inspect answers what it received
            prefix(routes, "/fn-upstream/inspect", (request, pathVariables) -> {
                StringBuilder received = new StringBuilder()
                    .append("method=").append(request.getMethodName()).append('\n')
                    .append("path=").append(request.getPath()).append('\n')
                    .append("query=").append(request.getUri().getRawQuery()).append('\n');
                for (String name : List.of("host", "x-custom", "x-forwarded-for", "x-forwarded-proto", "x-forwarded-host", "forwarded", "keep-alive")) {
                    received.append(name).append('=').append(String.join(", ", request.getHeaders().getAll(name))).append('\n');
                }
                return CompletableFuture.completedFuture(HttpResponse.ok(received.toString())
                    .contentType(MediaType.TEXT_PLAIN_TYPE)
                    .header("X-End-To-End", "kept")
                    .header(HttpHeaders.PROXY_AUTHENTICATE, "Basic"));
            });
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class ProxyRoutes implements HttpRoutes {
        private final AsyncRawHttpClient client;
        private final ProxyHttpClient proxyClient;
        private final BeanProvider<EmbeddedServer> server;
        private final int unreachablePort;

        ProxyRoutes(AsyncRawHttpClient client, ProxyHttpClient proxyClient, BeanProvider<EmbeddedServer> server,
                    @io.micronaut.context.annotation.Value("${" + UNREACHABLE_PORT + "}") int unreachablePort) {
            this.client = client;
            this.proxyClient = proxyClient;
            this.server = server;
            this.unreachablePort = unreachablePort;
        }

        @Override
        public void routes(HttpRouteBuilder routes) {
            prefix(routes, "/fn-proxy", (request, pathVariables) -> relay(request, upstream(request, rest(request, "/fn-proxy"))));
            prefix(routes, "/fn-unreachable", (request, pathVariables) -> relay(request,
                URI.create("http://localhost:" + unreachablePort + rest(request, "/fn-unreachable"))));
            prefix(routes, "/fn-proxied", (request, pathVariables) -> {
                // the proxy client claims the bytes of the body of the server request
                MutableHttpRequest<?> outbound = request.mutate().uri(upstream(request, rest(request, "/fn-proxied")));
                return Mono.from(proxyClient.proxy(outbound, ProxyRequestOptions.getDefault())).toFuture();
            });
        }

        /**
         * @param request The request
         * @param prefix  The prefix of its route
         * @return The rest of the raw path after the prefix, as the client sent it
         */
        private static String rest(HttpRequest<?> request, String prefix) {
            return request.getUri().getRawPath().substring(prefix.length());
        }

        private URI upstream(HttpRequest<?> request, String path) {
            String query = request.getUri().getRawQuery();
            String base = server.get().getURI().toString();
            if (base.endsWith("/")) {
                base = base.substring(0, base.length() - 1);
            }
            return URI.create(base + "/fn-upstream" + path + (query == null ? "" : "?" + query));
        }

        private CompletionStage<HttpResponse<?>> relay(HttpRequest<?> request, URI upstreamUri) {
            MutableHttpRequest<Object> outbound = HttpRequest.create(request.getMethod(), upstreamUri.toString(), request.getMethodName());
            request.getHeaders().forEach((name, values) -> values.forEach(value -> outbound.header(name, value)));
            HttpHeadersUtil.stripHopByHopHeaders(outbound.getHeaders());
            ForwardedHeaders.apply(request, outbound);
            CloseableByteBody body = ((ServerHttpRequest<?>) request).byteBody().move();
            return client.exchange(outbound, body, RawRequestOptions.proxy())
                .handle((response, failure) -> {
                    if (failure != null) {
                        return HttpResponse.status(HttpStatus.BAD_GATEWAY);
                    }
                    MutableHttpResponse<?> relayed = response.toMutableResponse();
                    HttpHeadersUtil.stripHopByHopHeaders(relayed.getHeaders());
                    return relayed;
                });
        }
    }

}
