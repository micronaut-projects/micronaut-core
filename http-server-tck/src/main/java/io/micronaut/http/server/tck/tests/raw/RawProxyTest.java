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

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpResponseWrapper;
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
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.CRC32;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A route that relays a request through the {@link RawHttpClient} and returns the raw client
 * response. The response bytes must be streamed back unchanged, response filters must keep them,
 * and the upstream response must be closed whenever the server does not send it.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class RawProxyTest {
    public static final String SPEC_NAME = "RawProxyTest";

    private static final int CHUNK_SIZE = 8192;
    private static final int LARGE_SIZE = 10 * 1024 * 1024;
    private static final int UPLOAD_SIZE = 16 * 1024 * 1024;
    private static final Map<String, Object> CONFIGURATION = Map.of(
        "micronaut.http.client.max-content-length", 64 * 1024 * 1024,
        "micronaut.http.client.read-timeout", "30s",
        "micronaut.server.max-request-size", 128 * 1024 * 1024
    );
    private static final int BACKPRESSURE_UPLOAD_SIZE = 64 * 1024 * 1024;
    private static final int BACKPRESSURE_BOUND = 32 * 1024 * 1024;

    @Test
    void largeStreamedBodyIsRelayedIntact() throws IOException {
        try (ServerUnderTest server = server()) {
            HttpResponse<byte[]> response = server.exchange(HttpRequest.GET("/raw-proxy/stream?size=" + LARGE_SIZE), byte[].class);
            assertEquals(HttpStatus.OK, response.getStatus());
            assertEquals(MediaType.APPLICATION_OCTET_STREAM, response.getContentType().map(MediaType::toString).orElse(null));
            assertArrayEquals(content(0, LARGE_SIZE), response.body());
        }
    }

    @Test
    void wrappedRawResponseKeepsBody() throws IOException {
        try (ServerUnderTest server = server()) {
            HttpResponse<byte[]> response = server.exchange(HttpRequest.GET("/raw-proxy/wrapped"), byte[].class);
            assertEquals(HttpStatus.OK, response.getStatus());
            assertArrayEquals(content(0, 1024 * 1024), response.body());
        }
    }

    @Test
    void responseFilterAddingHeaderKeepsBody() throws IOException {
        try (ServerUnderTest server = server()) {
            HttpResponse<byte[]> response = server.exchange(HttpRequest.GET("/raw-proxy/filtered"), byte[].class);
            assertEquals(HttpStatus.ACCEPTED, response.getStatus());
            assertEquals("true", response.getHeaders().get("X-Raw-Filter"));
            assertArrayEquals(content(0, 1024 * 1024), response.body());
        }
    }

    @Test
    void responseFilterReplacingResponseClosesUpstream() throws Exception {
        try (ServerUnderTest server = server()) {
            HttpResponse<String> response = server.exchange(HttpRequest.GET("/raw-proxy/replaced"), String.class);
            assertEquals(HttpStatus.OK, response.getStatus());
            assertEquals("replaced", response.body());
            assertUpstreamCancelled(server, "replaced");
        }
    }

    @Test
    void headRequestClosesUpstream() throws Exception {
        try (ServerUnderTest server = server()) {
            HttpResponse<byte[]> response = server.exchange(HttpRequest.HEAD("/raw-proxy/head"), byte[].class);
            assertEquals(HttpStatus.OK, response.getStatus());
            assertTrue(response.getBody().map(b -> b.length == 0).orElse(true));
            assertUpstreamCancelled(server, "head");
        }
    }

    @Test
    void streamingUploadIsRelayed() throws IOException {
        try (ServerUnderTest server = server()) {
            byte[] upload = content(0, UPLOAD_SIZE);
            HttpResponse<String> response = server.exchange(
                HttpRequest.POST("/raw-proxy/upload", upload).contentType(MediaType.APPLICATION_OCTET_STREAM_TYPE),
                String.class
            );
            assertEquals(HttpStatus.OK, response.getStatus());
            assertEquals(UPLOAD_SIZE + ":" + crc(upload), response.body());
        }
    }

    @Test
    void bodyClearedAfterReplacingTheBytesIsEmpty() throws Exception {
        try (ServerUnderTest server = server()) {
            // a filter replaced the bytes with an object body, then cleared it: the bytes stay closed
            HttpResponse<String> response = server.exchange(HttpRequest.GET("/raw-proxy/cleared"), String.class);
            assertEquals(HttpStatus.OK, response.getStatus());
            assertTrue(response.getBody().map(String::isEmpty).orElse(true), () -> "body: " + response.getBody());
            assertUpstreamCancelled(server, "cleared");
        }
    }

    @Test
    void wrapperBodyReplacesTheWrappedBytes() throws Exception {
        try (ServerUnderTest server = server()) {
            HttpResponse<String> response = server.exchange(HttpRequest.GET("/raw-proxy/wrapper-replaced"), String.class);
            assertEquals(HttpStatus.OK, response.getStatus());
            assertEquals("replacement", response.body());
            assertUpstreamCancelled(server, "wrapper-replaced");
        }
    }

    @Test
    void slowUpstreamStallsUpload() throws Exception {
        try (ServerUnderTest server = server();
             Socket socket = connect(server)) {
            UpstreamEvents events = server.getApplicationContext().getBean(UpstreamEvents.class);
            OutputStream out = socket.getOutputStream();
            AtomicLong sent = new AtomicLong();
            CompletableFuture<Void> upload = CompletableFuture.runAsync(() -> {
                try {
                    out.write(("POST /raw-proxy/slow-upload HTTP/1.1\r\nHost: localhost\r\n" +
                        "Content-Type: application/octet-stream\r\n" +
                        "Content-Length: " + BACKPRESSURE_UPLOAD_SIZE + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    byte[] chunk = new byte[64 * 1024];
                    for (int i = 0; i < BACKPRESSURE_UPLOAD_SIZE / chunk.length; i++) {
                        out.write(chunk);
                        sent.addAndGet(chunk.length);
                    }
                    out.flush();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            assertTrue(events.firstUploadChunk.await(20, TimeUnit.SECONDS), "The upstream did not receive the upload");
            // the upstream holds on to the first chunk. Give the sender time to fill every buffer on the way
            Thread.sleep(2000);
            long sentWhileStalled = sent.get();
            assertTrue(sentWhileStalled < BACKPRESSURE_BOUND,
                "The upload was not stalled by the slow upstream, " + sentWhileStalled + " bytes were accepted");

            events.releaseUpload.tryEmitEmpty();
            upload.get(60, TimeUnit.SECONDS);
            String response = readResponse(socket.getInputStream(), String.valueOf(BACKPRESSURE_UPLOAD_SIZE));
            assertTrue(response.startsWith("HTTP/1.1 200"), response);
            assertTrue(response.endsWith(String.valueOf(BACKPRESSURE_UPLOAD_SIZE)), response);
        }
    }

    private static String readResponse(InputStream in, String expectedEnd) throws IOException {
        ByteArrayOutputStream received = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        while (!received.toString(StandardCharsets.ISO_8859_1).contains(expectedEnd)) {
            int n = in.read(buffer);
            if (n == -1) {
                break;
            }
            received.write(buffer, 0, n);
        }
        return received.toString(StandardCharsets.ISO_8859_1);
    }

    @Test
    void clientDisconnectCancelsUpstream() throws Exception {
        try (ServerUnderTest server = server();
             Socket socket = connect(server)) {
            OutputStream out = socket.getOutputStream();
            out.write(("GET /raw-proxy/endless HTTP/1.1\r\nHost: localhost\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            InputStream in = socket.getInputStream();
            byte[] buffer = new byte[CHUNK_SIZE];
            long read = 0;
            while (read < 256 * 1024) {
                int n = in.read(buffer);
                if (n == -1) {
                    fail("Connection closed after " + read + " bytes");
                }
                read += n;
            }
            socket.close();
            assertUpstreamCancelled(server, "disconnect");
        }
    }

    @Test
    void upstreamFailureTruncatesResponse() throws Exception {
        try (ServerUnderTest server = server();
             Socket socket = connect(server)) {
            OutputStream out = socket.getOutputStream();
            out.write(("GET /raw-proxy/broken HTTP/1.1\r\nHost: localhost\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            ByteArrayOutputStream received = new ByteArrayOutputStream();
            InputStream in = socket.getInputStream();
            byte[] buffer = new byte[CHUNK_SIZE];
            try {
                int n;
                while ((n = in.read(buffer)) != -1) {
                    received.write(buffer, 0, n);
                }
            } catch (SocketTimeoutException e) {
                fail("The truncated response did not end, received " + received.size() + " bytes");
            } catch (IOException e) {
                // connection reset: the response was truncated
            }
            String head = received.toString(StandardCharsets.ISO_8859_1);
            assertTrue(head.startsWith("HTTP/1.1 200"), head.length() > 200 ? head.substring(0, 200) : head);
            assertTrue(received.size() >= BrokenUpstream.CHUNKS_BEFORE_FAILURE * CHUNK_SIZE, "received " + received.size());
            assertFalse(head.endsWith("\r\n0\r\n\r\n"), "A truncated response must not end like a complete chunked body");
        }
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME, CONFIGURATION);
    }

    private static Socket connect(ServerUnderTest server) throws IOException {
        int port = server.getPort().orElseThrow();
        Socket socket;
        if (server.getApplicationContext().getProperty("micronaut.server.ssl.enabled", Boolean.class).orElse(false)) {
            // HTTP/1.1 over TLS: without ALPN, a server that also speaks HTTP/2 falls back to HTTP/1.1
            socket = trustAll().getSocketFactory().createSocket("localhost", port);
        } else {
            socket = new Socket("localhost", port);
        }
        socket.setSoTimeout(20_000);
        return socket;
    }

    private static SSLContext trustAll() throws IOException {
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[]{new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    // the self-signed certificate of the server under test
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }}, null);
            return context;
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }
    }

    private static void assertUpstreamCancelled(ServerUnderTest server, String key) throws InterruptedException {
        UpstreamEvents events = server.getApplicationContext().getBean(UpstreamEvents.class);
        assertTrue(events.cancelled(key).await(20, TimeUnit.SECONDS), "The upstream response '" + key + "' was not cancelled");
    }

    static byte[] content(long offset, int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            long position = offset + i;
            bytes[i] = (byte) (position * 31 + (position >>> 13));
        }
        return bytes;
    }

    static long crc(byte[] bytes) {
        CRC32 crc = new CRC32();
        crc.update(bytes);
        return crc.getValue();
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class UpstreamEvents {
        final CountDownLatch firstUploadChunk = new CountDownLatch(1);
        final Sinks.Empty<Void> releaseUpload = Sinks.empty();
        private final Map<String, CountDownLatch> cancelled = new ConcurrentHashMap<>();

        CountDownLatch cancelled(String key) {
            return cancelled.computeIfAbsent(key, k -> new CountDownLatch(1));
        }
    }

    @Controller("/raw-upstream")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Upstream {
        private final UpstreamEvents events;

        Upstream(UpstreamEvents events) {
            this.events = events;
        }

        @Get(value = "/stream", produces = MediaType.APPLICATION_OCTET_STREAM)
        Publisher<byte[]> stream(@QueryValue long size) {
            return Flux.generate(() -> 0L, (offset, sink) -> {
                if (offset >= size) {
                    sink.complete();
                    return offset;
                }
                int n = (int) Math.min(CHUNK_SIZE, size - offset);
                sink.next(content(offset, n));
                return offset + n;
            });
        }

        @Get(value = "/endless", produces = MediaType.APPLICATION_OCTET_STREAM)
        Publisher<byte[]> endless(@QueryValue String key) {
            return Flux.<byte[]>generate(sink -> sink.next(new byte[CHUNK_SIZE]))
                .doOnCancel(() -> events.cancelled(key).countDown());
        }

        @Get(value = "/broken", produces = MediaType.APPLICATION_OCTET_STREAM)
        Publisher<byte[]> broken() {
            return Flux.range(0, BrokenUpstream.CHUNKS_BEFORE_FAILURE)
                .map(i -> content((long) i * CHUNK_SIZE, CHUNK_SIZE))
                .concatWith(Mono.delay(Duration.ofMillis(200)).then(Mono.error(new IllegalStateException("Upstream failure"))));
        }

        @Post(value = "/slow-upload", consumes = MediaType.APPLICATION_OCTET_STREAM, produces = MediaType.TEXT_PLAIN)
        Mono<String> slowUpload(@Body Publisher<byte[]> body) {
            AtomicBoolean first = new AtomicBoolean(true);
            return Flux.from(body)
                .concatMap(bytes -> {
                    if (first.getAndSet(false)) {
                        // hold on to the first chunk until the test releases the upload
                        events.firstUploadChunk.countDown();
                        return events.releaseUpload.asMono().thenReturn((long) bytes.length);
                    }
                    return Mono.just((long) bytes.length);
                }, 1)
                .reduce(0L, Long::sum)
                .map(String::valueOf);
        }

        @Post(value = "/upload", consumes = MediaType.APPLICATION_OCTET_STREAM, produces = MediaType.TEXT_PLAIN)
        Mono<String> upload(@Body Publisher<byte[]> body) {
            return Flux.from(body)
                .collect(UploadDigest::new, UploadDigest::update)
                .map(UploadDigest::toString);
        }
    }

    static final class UploadDigest {
        private final CRC32 crc = new CRC32();
        private long length;

        void update(byte[] bytes) {
            length += bytes.length;
            crc.update(bytes);
        }

        @Override
        public String toString() {
            return length + ":" + crc.getValue();
        }
    }

    static final class BrokenUpstream {
        static final int CHUNKS_BEFORE_FAILURE = 16;

        private BrokenUpstream() {
        }
    }

    @Controller("/raw-proxy")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Proxy {
        private final RawHttpClient client;
        private final EmbeddedServer embeddedServer;

        Proxy(RawHttpClient client, EmbeddedServer embeddedServer) {
            this.client = client;
            this.embeddedServer = embeddedServer;
        }

        @Post(value = "/slow-upload", consumes = MediaType.ALL)
        Mono<HttpResponse<?>> slowUpload(ServerHttpRequest<?> request) {
            return relay(request, "/raw-upstream/slow-upload");
        }

        @Get("/stream")
        Mono<HttpResponse<?>> stream(ServerHttpRequest<?> request, @QueryValue long size) {
            return relay(request, "/raw-upstream/stream?size=" + size);
        }

        @Get("/wrapped")
        @SuppressWarnings("unchecked")
        Mono<HttpResponse<?>> wrapped(ServerHttpRequest<?> request) {
            // a wrapper around the raw response, e.g. from a library that decorates responses
            return relay(request, "/raw-upstream/stream?size=" + (1024 * 1024))
                .map(response -> new HttpResponseWrapper<>((HttpResponse<Object>) response));
        }

        @Get("/filtered")
        Mono<HttpResponse<?>> filtered(ServerHttpRequest<?> request) {
            return relay(request, "/raw-upstream/stream?size=" + (1024 * 1024));
        }

        @Get("/replaced")
        Mono<HttpResponse<?>> replaced(ServerHttpRequest<?> request) {
            return relay(request, "/raw-upstream/endless?key=replaced");
        }

        @Get("/head")
        Mono<HttpResponse<?>> head(ServerHttpRequest<?> request) {
            // always a GET upstream, so the upstream sends a body that the server must drop
            return relay(request, HttpRequest.GET(upstream("/raw-upstream/endless?key=head")));
        }

        @Get("/cleared")
        Mono<HttpResponse<?>> cleared(ServerHttpRequest<?> request) {
            return relay(request, "/raw-upstream/endless?key=cleared");
        }

        @Get("/wrapper-replaced")
        @SuppressWarnings("unchecked")
        Mono<HttpResponse<?>> wrapperReplaced(ServerHttpRequest<?> request) {
            // a wrapper whose object body supersedes the bytes of the wrapped raw response
            return relay(request, "/raw-upstream/endless?key=wrapper-replaced")
                .map(response -> new HttpResponseWrapper<>((HttpResponse<Object>) response) {
                    @Override
                    public Optional<Object> getBody() {
                        return Optional.of("replacement");
                    }
                });
        }

        @Get("/endless")
        Mono<HttpResponse<?>> endless(ServerHttpRequest<?> request) {
            return relay(request, "/raw-upstream/endless?key=disconnect");
        }

        @Get("/broken")
        Mono<HttpResponse<?>> broken(ServerHttpRequest<?> request) {
            return relay(request, "/raw-upstream/broken");
        }

        @Post(value = "/upload", consumes = MediaType.ALL)
        Mono<HttpResponse<?>> upload(ServerHttpRequest<?> request) {
            return relay(request, "/raw-upstream/upload");
        }

        private Mono<HttpResponse<?>> relay(ServerHttpRequest<?> request, String path) {
            MutableHttpRequest<Object> outbound = HttpRequest.create(request.getMethod(), upstream(path).toString());
            request.getHeaders().contentType().ifPresent(outbound::contentType);
            return relay(request, outbound);
        }

        private Mono<HttpResponse<?>> relay(ServerHttpRequest<?> request, MutableHttpRequest<?> outbound) {
            return Mono.<HttpResponse<?>>from(client.exchange(outbound, request.byteBody().move(), null));
        }

        private URI upstream(String path) {
            return embeddedServer.getURI().resolve(path);
        }
    }

    @ServerFilter("/raw-proxy/filtered")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class AddHeaderFilter {
        @ResponseFilter
        void addHeader(MutableHttpResponse<?> response) {
            response.status(HttpStatus.ACCEPTED);
            response.header("X-Raw-Filter", "true");
        }
    }

    @ServerFilter("/raw-proxy/cleared")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class ClearFilter {
        @ResponseFilter
        void clear(MutableHttpResponse<?> response) {
            response.body("replacement");
            response.body(null);
        }
    }

    @ServerFilter("/raw-proxy/replaced")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class ReplaceFilter {
        @ResponseFilter
        HttpResponse<?> replace(HttpResponse<?> response) {
            return HttpResponse.ok("replaced").contentType(MediaType.TEXT_PLAIN_TYPE).header(HttpHeaders.CACHE_CONTROL, "no-store");
        }
    }
}
