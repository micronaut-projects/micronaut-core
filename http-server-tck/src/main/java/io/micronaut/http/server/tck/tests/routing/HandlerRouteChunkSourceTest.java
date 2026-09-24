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

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.body.ChunkSource;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.sse.Event;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.net.Socket;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.micronaut.http.server.tck.tests.routing.HandlerRouteServerSentEventsTest.connect;
import static io.micronaut.http.server.tck.tests.routing.HandlerRouteServerSentEventsTest.count;
import static io.micronaut.http.server.tck.tests.routing.HandlerRouteServerSentEventsTest.readToEnd;
import static io.micronaut.http.server.tck.tests.routing.HandlerRouteServerSentEventsTest.readUntil;
import static io.micronaut.http.server.tck.tests.routing.HandlerRouteServerSentEventsTest.request;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A {@link ChunkSource} response body: the server pulls its elements one at a time, while the
 * client keeps up, and writes them like the elements of a publisher body, without Reactive
 * Streams. The source is closed when the response ends, fails, the client disconnects, or the
 * body is not written.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HandlerRouteChunkSourceTest {
    public static final String SPEC_NAME = "HandlerRouteChunkSourceTest";
    private static final int CHUNK = 64 * 1024;
    private static final int LARGE_CHUNKS = 128;
    private static final int SLOW_ELEMENTS = 20_000;
    private static final String KILOBYTE = "x".repeat(1000);

    @Test
    void jsonElementsAreAnArray() throws Exception {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/chunks/json"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("[{\"n\":1},{\"n\":2},{\"n\":3}]")
                .build());
            assertClosed(server, "json");
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/chunks/empty"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("[]")
                .build());
            assertClosed(server, "empty");
        }
    }

    @Test
    void elementsThatCompleteLater() throws Exception {
        try (ServerUnderTest server = server()) {
            HttpResponse<String> response = server.exchange(HttpRequest.GET("/chunks/async"), String.class);
            assertEquals("abcdefghij", response.body());
            assertTrue(response.getHeaders().get(HttpHeaders.CONTENT_TYPE).startsWith(MediaType.TEXT_PLAIN));
            assertClosed(server, "async");
        }
    }

    @Test
    void largeBody() throws Exception {
        try (ServerUnderTest server = server()) {
            HttpResponse<byte[]> response = server.exchange(HttpRequest.GET("/chunks/large"), byte[].class);
            byte[] body = response.body();
            assertEquals(LARGE_CHUNKS * CHUNK, body.length);
            byte[] expected = new byte[CHUNK];
            for (int i = 0; i < LARGE_CHUNKS; i++) {
                Arrays.fill(expected, (byte) i);
                assertArrayEquals(expected, Arrays.copyOfRange(body, i * CHUNK, (i + 1) * CHUNK), "chunk " + i);
            }
            assertClosed(server, "large");
        }
    }

    @Test
    void eventStream() throws Exception {
        try (ServerUnderTest server = server()) {
            HttpResponse<String> response = server.exchange(HttpRequest.GET("/chunks/events"), String.class);
            assertTrue(response.getHeaders().get(HttpHeaders.CONTENT_TYPE).startsWith(MediaType.TEXT_EVENT_STREAM));
            assertEquals("id: 1\ndata: a\n\nid: 2\ndata: b\n\n", response.body());
        }
    }

    @Test
    void failureOfTheFirstElementIsAnsweredLikeAnErrorOfTheRoute() throws Exception {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertThrows(server, HttpRequest.GET("/chunks/refuse"), HttpResponseAssertion.builder()
                .status(HttpStatus.CONFLICT)
                .build());
            assertClosed(server, "refuse");
        }
    }

    @Test
    void failureOfALaterElementEndsTheResponseAbruptly() throws Exception {
        try (ServerUnderTest server = server();
             Socket socket = connect(server)) {
            request(socket, "GET /chunks/break HTTP/1.1\r\nHost: localhost\r\n\r\n");
            String received = readToEnd(socket.getInputStream());
            assertTrue(received.startsWith("HTTP/1.1 200"), received);
            assertTrue(received.contains("first"), received);
            assertFalse(received.endsWith("0\r\n\r\n"), "A failed body must not end like a complete chunked body: " + received);
            assertClosed(server, "break");
        }
    }

    @Test
    void headRequestClosesTheSourceWithoutPullingIt() throws Exception {
        try (ServerUnderTest server = server()) {
            HttpResponse<String> response = server.exchange(HttpRequest.HEAD("/chunks/head"), String.class);
            assertEquals(HttpStatus.OK, response.getStatus());
            assertTrue(response.getBody(String.class).orElse("").isEmpty());
            assertClosed(server, "head");
            assertEquals(0, recorder(server).pulled.get());
        }
    }

    @Test
    void slowReaderPausesTheSource() throws Exception {
        try (ServerUnderTest server = server();
             Socket socket = connect(server)) {
            Recorder recorder = recorder(server);
            request(socket, "GET /chunks/slow HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");
            // the client does not read: the source must not be pulled once the buffers are full
            Thread.sleep(2000);
            int pulledWhileStalled = recorder.pulled.get();
            assertTrue(pulledWhileStalled < SLOW_ELEMENTS / 2, "The source was pulled without the client reading: " + pulledWhileStalled + " elements");
            String received = readToEnd(socket.getInputStream());
            assertEquals(SLOW_ELEMENTS, count(received, KILOBYTE));
            assertClosed(server, "slow");
        }
    }

    @Test
    void clientDisconnectClosesTheSource() throws Exception {
        try (ServerUnderTest server = server()) {
            try (Socket socket = connect(server)) {
                request(socket, "GET /chunks/endless HTTP/1.1\r\nHost: localhost\r\n\r\n");
                readUntil(socket.getInputStream(), "tick");
            }
            assertClosed(server, "endless");
        }
    }

    private static void assertClosed(ServerUnderTest server, String key) throws Exception {
        recorder(server).closed(key).get(20, TimeUnit.SECONDS);
    }

    private static Recorder recorder(ServerUnderTest server) {
        return server.getApplicationContext().getBean(Recorder.class);
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Recorder {
        final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        final Map<String, CompletableFuture<Void>> closed = new ConcurrentHashMap<>();
        final AtomicInteger pulled = new AtomicInteger();

        CompletableFuture<Void> closed(String key) {
            return closed.computeIfAbsent(key, k -> new CompletableFuture<>());
        }

        /**
         * A source of the given elements that records when it is closed.
         */
        <T> ChunkSource<T> source(String key, Iterator<T> elements) {
            return new ChunkSource<>() {
                @Override
                public CompletionStage<Optional<T>> next() {
                    pulled.incrementAndGet();
                    return CompletableFuture.completedFuture(elements.hasNext() ? Optional.of(elements.next()) : Optional.empty());
                }

                @Override
                public void close() {
                    closed(key).complete(null);
                }
            };
        }

        @PreDestroy
        void close() {
            scheduler.shutdownNow();
        }
    }

    @Factory
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes {

        @Singleton
        HttpRoutes chunkRoutes(Recorder recorder) {
            return routes -> {
                routes.GET("/chunks/json", (request, pathVariables) -> HttpResponse.ok(
                    recorder.source("json", List.of(Map.of("n", 1), Map.of("n", 2), Map.of("n", 3)).iterator())));
                routes.GET("/chunks/empty", (request, pathVariables) -> HttpResponse.ok(
                    recorder.source("empty", List.of().iterator())));
                routes.GET("/chunks/async", (request, pathVariables) -> {
                    Iterator<String> letters = List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j").iterator();
                    ChunkSource<String> source = new ChunkSource<>() {
                        @Override
                        public CompletionStage<Optional<String>> next() {
                            CompletableFuture<Optional<String>> next = new CompletableFuture<>();
                            recorder.scheduler.schedule(() -> next.complete(letters.hasNext() ? Optional.of(letters.next()) : Optional.empty()), 5, TimeUnit.MILLISECONDS);
                            return next;
                        }

                        @Override
                        public void close() {
                            recorder.closed("async").complete(null);
                        }
                    };
                    return HttpResponse.ok(source).contentType(MediaType.TEXT_PLAIN_TYPE);
                });
                routes.GET("/chunks/large", (request, pathVariables) -> HttpResponse.ok(recorder.source("large", new Iterator<byte[]>() {
                    int i;

                    @Override
                    public boolean hasNext() {
                        return i < LARGE_CHUNKS;
                    }

                    @Override
                    public byte[] next() {
                        byte[] chunk = new byte[CHUNK];
                        Arrays.fill(chunk, (byte) i++);
                        return chunk;
                    }
                })).contentType(MediaType.APPLICATION_OCTET_STREAM_TYPE));
                routes.GET("/chunks/events", (request, pathVariables) -> HttpResponse.ok(recorder.source("events",
                    List.of(Event.of("a").id("1"), Event.of("b").id("2")).iterator())).contentType(MediaType.TEXT_EVENT_STREAM_TYPE));
                routes.GET("/chunks/refuse", (request, pathVariables) -> HttpResponse.ok(new ChunkSource<String>() {
                    @Override
                    public CompletionStage<Optional<String>> next() {
                        return CompletableFuture.failedFuture(new HttpStatusException(HttpStatus.CONFLICT, "conflict"));
                    }

                    @Override
                    public void close() {
                        recorder.closed("refuse").complete(null);
                    }
                }));
                routes.GET("/chunks/break", (request, pathVariables) -> {
                    AtomicInteger calls = new AtomicInteger();
                    ChunkSource<String> source = new ChunkSource<>() {
                        @Override
                        public CompletionStage<Optional<String>> next() {
                            if (calls.getAndIncrement() == 0) {
                                return CompletableFuture.completedFuture(Optional.of("first"));
                            }
                            CompletableFuture<Optional<String>> next = new CompletableFuture<>();
                            recorder.scheduler.schedule(() -> next.completeExceptionally(new IllegalStateException("broken")), 100, TimeUnit.MILLISECONDS);
                            return next;
                        }

                        @Override
                        public void close() {
                            recorder.closed("break").complete(null);
                        }
                    };
                    return HttpResponse.ok(source).contentType(MediaType.TEXT_PLAIN_TYPE);
                });
                routes.GET("/chunks/head", (request, pathVariables) -> HttpResponse.ok(recorder.source("head", List.of("never").iterator()))
                    .contentType(MediaType.TEXT_PLAIN_TYPE));
                routes.GET("/chunks/slow", (request, pathVariables) -> HttpResponse.ok(recorder.source("slow", new Iterator<String>() {
                    int i;

                    @Override
                    public boolean hasNext() {
                        return i < SLOW_ELEMENTS;
                    }

                    @Override
                    public String next() {
                        i++;
                        return KILOBYTE;
                    }
                })).contentType(MediaType.TEXT_PLAIN_TYPE));
                routes.GET("/chunks/endless", (request, pathVariables) -> HttpResponse.ok(recorder.source("endless", new Iterator<String>() {
                    @Override
                    public boolean hasNext() {
                        return true;
                    }

                    @Override
                    public String next() {
                        return "tick\n";
                    }
                })).contentType(MediaType.TEXT_PLAIN_TYPE));
            };
        }
    }
}
