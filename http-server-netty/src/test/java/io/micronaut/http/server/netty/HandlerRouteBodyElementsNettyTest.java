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
package io.micronaut.http.server.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpResponseWrapper;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.body.BodyElements;
import io.micronaut.http.body.TypedMessageBodyWriter;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.sse.Event;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.HttpRoutes;
import io.netty.util.internal.ThreadExecutorMap;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Netty specifics of a {@link BodyElements} body: a blocking writer of the elements runs on a
 * worker thread, not on the event loop, like the writer of the elements of a publisher body. And
 * how the server encodes and closes the elements: a JSON array for the declared type of the route,
 * the bodies of the route released when the elements close, no body for a HEAD request, and
 * elements a filter replaces are closed, elements it wraps are written.
 */
class HandlerRouteBodyElementsNettyTest {

    private static final String SPEC_NAME = "HandlerRouteBodyElementsNettyTest";

    @Test
    void blockingWriterOfTheElementsRunsOnAWorker() throws IOException {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.port", -1))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class);
            server.start();
            String response = get(server, "/netty-elements/blocking");
            assertTrue(response.startsWith("HTTP/1.1 200"), response);
            // each element in its own chunk or not: both were written on a worker
            assertEquals(2, response.split("worker,", -1).length - 1, response);
            assertFalse(response.contains("event-loop"), response);
        }
    }

    @Test
    void eachElementClassGetsItsOwnWriter() throws IOException {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.port", -1))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class);
            server.start();
            // the writer of an element class is reused for the next element of that class only
            String response = get(server, "/netty-elements/mixed");
            assertTrue(response.startsWith("HTTP/1.1 200"), response);
            assertEquals("worker,worker,ab,worker,c", chunks(response));
        }
    }

    @Test
    void theServerEncodesAndClosesTheElements() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.port", -1))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class);
            server.start();
            Recorder recorder = ctx.getBean(Recorder.class);
            String json = get(server, "/netty-elements/json");
            assertTrue(json.startsWith("HTTP/1.1 200"), json);
            assertTrue(head(json).contains("content-type: application/json"), json);
            assertEquals("[1,2,3]", chunks(json));
            assertEquals("[]", chunks(get(server, "/netty-elements/empty")));
            // the elements of the request body: the body is released when they close
            String echo = exchange(server, "POST /netty-elements/echo HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/json\r\nContent-Length: 5\r\nConnection: close\r\n\r\n[1,2]");
            assertEquals("[1,2]", chunks(echo));
            // a HEAD request: the elements are never pulled
            String head = exchange(server, "HEAD /netty-elements/head HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");
            assertTrue(head.startsWith("HTTP/1.1 200"), head);
            recorder.closed("head").get(10, TimeUnit.SECONDS);
            assertEquals(0, recorder.pulled("head"));
            // replaced by a filter: closed, even when closing fails
            assertTrue(get(server, "/netty-elements/replaced").endsWith("replaced"));
            recorder.closed("replaced").get(10, TimeUnit.SECONDS);
            // wrapped by a filter: written
            assertEquals("hello", chunks(get(server, "/netty-elements/wrapped")));
            recorder.closed("wrapped").get(10, TimeUnit.SECONDS);
            // events: not compressed
            String events = exchange(server, "GET /netty-elements/events HTTP/1.1\r\nHost: localhost\r\nAccept-Encoding: gzip\r\nConnection: close\r\n\r\n");
            assertFalse(head(events).contains("content-encoding"), events);
            assertTrue(events.contains("data: one\n\n"), events);
        }
    }

    private static String get(EmbeddedServer server, String path) throws IOException {
        return exchange(server, "GET " + path + " HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");
    }

    private static String exchange(EmbeddedServer server, String request) throws IOException {
        try (Socket socket = new Socket("localhost", server.getPort())) {
            socket.setSoTimeout(20_000);
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String head(String response) {
        return response.substring(0, Math.max(0, response.indexOf("\r\n\r\n"))).toLowerCase(Locale.ROOT);
    }

    /**
     * @param response A response with a chunked body
     * @return The body
     */
    private static String chunks(String response) {
        String rest = response.substring(response.indexOf("\r\n\r\n") + 4);
        StringBuilder body = new StringBuilder();
        while (true) {
            int end = rest.indexOf("\r\n");
            int size = Integer.parseInt(rest.substring(0, end).trim(), 16);
            if (size == 0) {
                return body.toString();
            }
            body.append(rest, end + 2, end + 2 + size);
            rest = rest.substring(end + 2 + size + 2);
        }
    }

    record Blocking() {
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Recorder {
        final Map<String, CompletableFuture<Void>> closed = new ConcurrentHashMap<>();
        final Map<String, AtomicInteger> pulls = new ConcurrentHashMap<>();

        CompletableFuture<Void> closed(String key) {
            return closed.computeIfAbsent(key, k -> new CompletableFuture<>());
        }

        int pulled(String key) {
            return pulls.computeIfAbsent(key, k -> new AtomicInteger()).get();
        }

        /**
         * A source of the given elements that records its reads and when it is closed.
         */
        <T> BodyElements<T> source(String key, List<T> values) {
            Iterator<T> elements = values.iterator();
            return BodyElements.of(() -> {
                pulls.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
                return CompletableFuture.completedFuture(elements.hasNext() ? Optional.of(elements.next()) : Optional.empty());
            }, () -> closed(key).complete(null));
        }
    }

    /**
     * Writes the thread it runs on.
     */
    @Singleton
    @Produces(MediaType.TEXT_PLAIN)
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class BlockingWriter implements TypedMessageBodyWriter<Blocking> {
        @Override
        public Argument<Blocking> getType() {
            return Argument.of(Blocking.class);
        }

        @Override
        public boolean isBlocking() {
            return true;
        }

        @Override
        public void writeTo(Argument<Blocking> type, MediaType mediaType, Blocking value, MutableHeaders headers, OutputStream out) throws CodecException {
            try {
                out.write((ThreadExecutorMap.currentExecutor() == null ? "worker," : "event-loop,").getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new CodecException("Cannot write", e);
            }
        }
    }

    @Factory
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes {
        @Singleton
        HttpRoutes nettyElementRoutes(Recorder recorder) {
            return routes -> {
                routes.GET("/netty-elements/blocking", (request, pathVariables) -> {
                    var elements = List.of(new Blocking(), new Blocking()).iterator();
                    return HttpResponse.ok(BodyElements.of(() ->
                            CompletableFuture.completedFuture(elements.hasNext() ? Optional.of(elements.next()) : Optional.<Blocking>empty())))
                        .contentType(MediaType.TEXT_PLAIN_TYPE);
                });
                routes.GET("/netty-elements/mixed", (request, pathVariables) ->
                    HttpResponse.ok(recorder.source("mixed", List.<Object>of(new Blocking(), new Blocking(), "a", "b,", new Blocking(), "c")))
                        .contentType(MediaType.TEXT_PLAIN_TYPE));
                routes.GET("/netty-elements/json")
                    .responseType(Argument.of(BodyElements.class, Integer.class))
                    .handle((request, pathVariables) -> HttpResponse.ok(recorder.source("json", List.of(1, 2, 3))));
                routes.GET("/netty-elements/empty", (request, pathVariables) -> HttpResponse.ok(recorder.source("empty", List.of())));
                routes.POST("/netty-elements/echo").body().handleAsync((request, pathVariables, body) ->
                    CompletableFuture.completedStage(HttpResponse.ok(body.elements(Integer.class)).contentType(MediaType.APPLICATION_JSON_TYPE)));
                routes.GET("/netty-elements/head", (request, pathVariables) -> HttpResponse.ok(recorder.source("head", List.of("never"))));
                routes.GET("/netty-elements/replaced")
                    .afterReplacing((request, response) -> HttpResponse.ok("replaced").contentType(MediaType.TEXT_PLAIN_TYPE)).and()
                    .handle((request, pathVariables) -> HttpResponse.ok(BodyElements.of(
                            () -> CompletableFuture.completedFuture(Optional.of("never")),
                            () -> {
                                recorder.closed("replaced").complete(null);
                                throw new IllegalStateException("cannot close");
                            }))
                        .contentType(MediaType.TEXT_PLAIN_TYPE));
                routes.GET("/netty-elements/wrapped")
                    .afterReplacing((request, response) -> new HttpResponseWrapper<>(response)).and()
                    .handle((request, pathVariables) -> HttpResponse.ok(recorder.source("wrapped", List.of("hello")))
                        .contentType(MediaType.TEXT_PLAIN_TYPE));
                routes.GET("/netty-elements/events", (request, pathVariables) -> HttpResponse.ok(recorder.source("events", List.of(Event.of("one"))))
                    .contentType(MediaType.TEXT_EVENT_STREAM_TYPE));
            };
        }
    }
}
