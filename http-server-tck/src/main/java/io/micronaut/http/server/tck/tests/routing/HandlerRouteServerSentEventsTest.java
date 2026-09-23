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
import io.micronaut.core.propagation.MutablePropagatedContext;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.exceptions.ConnectionClosedException;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.sse.Event;
import io.micronaut.http.sse.SseEmitter;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Server-sent events routes of the route builder: the handler pushes events to an
 * {@link SseEmitter}, without Reactive Streams. The events keep their order and fields, the
 * response is sent with the first event (so that a failure before is answered by the error
 * routes), a slow client pauses the sender, a disconnect closes the emitter, and the stream is
 * never compressed.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HandlerRouteServerSentEventsTest {
    public static final String SPEC_NAME = "HandlerRouteServerSentEventsTest";
    private static final String TRACE = "X-Trace";
    private static final int FLOOD_EVENTS = 20_000;
    private static final String KILOBYTE = "x".repeat(1000);

    @Test
    void eventsKeepTheirFieldsAndOrder() throws IOException {
        try (ServerUnderTest server = server()) {
            HttpResponse<String> response = server.exchange(HttpRequest.GET("/sse/fields"), String.class);
            assertEquals(HttpStatus.OK, response.getStatus());
            String contentType = response.getHeaders().get(HttpHeaders.CONTENT_TYPE);
            assertTrue(contentType != null && contentType.startsWith(MediaType.TEXT_EVENT_STREAM), contentType);
            assertEquals("no-cache", response.getHeaders().get(HttpHeaders.CACHE_CONTROL));
            assertEquals("""
                : hello

                id: 1
                event: first
                data: one

                id: 2
                retry: 5000
                data: two
                data: lines

                : json
                data: {"k":"v"}

                """, response.body());
            assertNull(recorder(server).closed("fields").getNow(null));
        }
    }

    @Test
    void sendsFromAnotherThreadKeepTheirOrder() throws IOException {
        try (ServerUnderTest server = server()) {
            String body = server.exchange(HttpRequest.GET("/sse/ordered"), String.class).body();
            StringBuilder expected = new StringBuilder();
            for (int i = 0; i < 500; i++) {
                expected.append("data: ").append(i).append("\n\n");
            }
            assertEquals(expected.toString(), body);
        }
    }

    @Test
    void lastEventIdOfAReconnection() throws IOException {
        try (ServerUnderTest server = server()) {
            assertEquals("data: resumed after 41\n\n",
                server.exchange(HttpRequest.GET("/sse/resume").header("Last-Event-ID", "41"), String.class).body());
            assertEquals("data: first connection\n\n",
                server.exchange(HttpRequest.GET("/sse/resume"), String.class).body());
        }
    }

    @Test
    void heartbeatOpensTheStreamAndFillsTheSilence() throws IOException {
        try (ServerUnderTest server = server()) {
            String body = server.exchange(HttpRequest.GET("/sse/heartbeat"), String.class).body();
            assertTrue(body.startsWith(":\n\n"), body);
            assertTrue(body.endsWith("data: done\n\n"), body);
        }
    }

    @Test
    void failureBeforeTheFirstEventIsAnsweredByTheErrorRoutes() throws Exception {
        try (ServerUnderTest server = server()) {
            // thrown by the handler
            assertStatus(server, "/sse/refuse", HttpStatus.FORBIDDEN);
            // failed later, from another thread
            assertStatus(server, "/sse/refuse-later", HttpStatus.CONFLICT);
            // an error route of the builder
            assertStatus(server, "/sse/refuse-custom", HttpStatus.I_AM_A_TEAPOT);
            Throwable closed = recorder(server).closed("refuse-later").get(20, TimeUnit.SECONDS);
            assertInstanceOf(HttpStatusException.class, closed);
        }
    }

    @Test
    void failureAfterTheFirstEventEndsTheStreamAbruptly() throws Exception {
        try (ServerUnderTest server = server();
             Socket socket = connect(server)) {
            request(socket, "GET /sse/break HTTP/1.1\r\nHost: localhost\r\n\r\n");
            String received = readToEnd(socket.getInputStream());
            assertTrue(received.startsWith("HTTP/1.1 200"), received);
            assertTrue(received.contains("data: one"), received);
            assertFalse(received.endsWith("0\r\n\r\n"), "A failed stream must not end like a complete chunked body: " + received);
            Throwable closed = recorder(server).closed("break").get(20, TimeUnit.SECONDS);
            assertEquals("broken", closed.getMessage());
        }
    }

    @Test
    void eventStreamIsNotCompressed() throws Exception {
        try (ServerUnderTest server = server();
             Socket socket = connect(server)) {
            request(socket, "GET /sse/fields HTTP/1.1\r\nHost: localhost\r\nAccept-Encoding: gzip, deflate, br, zstd, snappy\r\nConnection: close\r\n\r\n");
            String received = readToEnd(socket.getInputStream());
            String head = received.substring(0, received.indexOf("\r\n\r\n")).toLowerCase();
            assertFalse(head.contains("content-encoding"), head);
            assertTrue(received.contains("data: one"), received);
        }
    }

    @Test
    void headRequestSendsTheHeadersOnly() throws Exception {
        try (ServerUnderTest server = server()) {
            HttpResponse<String> response = server.exchange(HttpRequest.HEAD("/sse/head"), String.class);
            assertEquals(HttpStatus.OK, response.getStatus());
            String contentType = response.getHeaders().get(HttpHeaders.CONTENT_TYPE);
            assertTrue(contentType != null && contentType.startsWith(MediaType.TEXT_EVENT_STREAM), contentType);
            assertTrue(response.getBody(String.class).orElse("").isEmpty());
            // closed normally: a HEAD response has no body
            assertNull(recorder(server).closed("head").get(20, TimeUnit.SECONDS));
        }
    }

    @Test
    void clientDisconnectClosesTheEmitter() throws Exception {
        try (ServerUnderTest server = server()) {
            try (Socket socket = connect(server)) {
                request(socket, "GET /sse/ticks HTTP/1.1\r\nHost: localhost\r\n\r\n");
                readUntil(socket.getInputStream(), "data: tick");
            }
            Recorder recorder = recorder(server);
            assertInstanceOf(ConnectionClosedException.class, recorder.closed("ticks").get(20, TimeUnit.SECONDS));
            // a send after the disconnect fails
            assertInstanceOf(ConnectionClosedException.class, recorder.failedSend.get(20, TimeUnit.SECONDS));
        }
    }

    @Test
    void slowReaderPausesABlockingSender() throws Exception {
        try (ServerUnderTest server = server();
             Socket socket = connect(server)) {
            Recorder recorder = recorder(server);
            request(socket, "GET /sse/flood HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");
            // the client does not read: the sender must stall once every buffer on the way is full
            Thread.sleep(2000);
            int sentWhileStalled = recorder.sent.get();
            assertTrue(sentWhileStalled < FLOOD_EVENTS / 2, "The sender did not wait for the client, it sent " + sentWhileStalled + " events");
            String received = readToEnd(socket.getInputStream());
            assertEquals(FLOOD_EVENTS, count(received, "data: "), "all the events arrive once the client reads");
            assertTrue(received.contains("id: " + (FLOOD_EVENTS - 1) + "\n"), "the last event");
            assertNull(recorder.closed("flood").get(20, TimeUnit.SECONDS));
        }
    }

    @Test
    void senderThatDoesNotWaitOverflowsTheQueue() throws Exception {
        try (ServerUnderTest server = server();
             Socket socket = connect(server)) {
            request(socket, "GET /sse/overflow HTTP/1.1\r\nHost: localhost\r\n\r\n");
            // the client does not read
            Throwable closed = recorder(server).closed("overflow").get(30, TimeUnit.SECONDS);
            assertInstanceOf(ConnectionClosedException.class, closed);
            assertTrue(closed.getMessage().contains("does not read"), closed.getMessage());
        }
    }

    @Test
    void blockingHandlerOnAnExecutor() throws IOException {
        try (ServerUnderTest server = server()) {
            String body = server.exchange(HttpRequest.GET("/sse/blocking"), String.class).body();
            StringBuilder expected = new StringBuilder();
            for (int i = 0; i < 100; i++) {
                expected.append("data: ").append(i).append("\n\n");
            }
            assertEquals(expected.toString(), body);
        }
    }

    @Test
    void handlerAndContinuationsSeeThePropagatedContext() throws Exception {
        try (ServerUnderTest server = server()) {
            for (String path : new String[]{"/sse/context", "/sse/context-blocking"}) {
                String body = server.exchange(HttpRequest.GET(path).header(TRACE, "t1"), String.class).body();
                assertEquals("data: handler:t1\n\ndata: continuation:t1\n\n", body, path);
            }
            assertEquals("t1", recorder(server).closedContext.get(20, TimeUnit.SECONDS));
        }
    }

    @Test
    void responseFiltersSeeTheHeaders() throws IOException {
        try (ServerUnderTest server = server()) {
            HttpResponse<String> response = server.exchange(HttpRequest.GET("/sse/filtered"), String.class);
            assertEquals("route", response.getHeaders().get("X-Route-Filter"));
            assertEquals("server", response.getHeaders().get("X-Server-Filter"));
            assertEquals("data: filtered\n\n", response.body());
        }
    }

    @Test
    void responseReplacedByAFilterClosesTheEmitter() throws Exception {
        try (ServerUnderTest server = server()) {
            HttpResponse<String> response = server.exchange(HttpRequest.GET("/sse/replaced"), String.class);
            assertEquals("replaced", response.body());
            assertInstanceOf(ConnectionClosedException.class, recorder(server).closed("replaced").get(20, TimeUnit.SECONDS));
        }
    }

    private static void assertStatus(ServerUnderTest server, String path, HttpStatus status) {
        AssertionUtils.assertThrows(server, HttpRequest.GET(path), HttpResponseAssertion.builder().status(status).build());
    }

    private static Recorder recorder(ServerUnderTest server) {
        return server.getApplicationContext().getBean(Recorder.class);
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    static int count(String text, String part) {
        int n = 0;
        for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + part.length())) {
            n++;
        }
        return n;
    }

    static void request(Socket socket, String request) throws IOException {
        OutputStream out = socket.getOutputStream();
        out.write(request.getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    static String readUntil(InputStream in, String expected) throws IOException {
        ByteArrayOutputStream received = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        while (!received.toString(StandardCharsets.ISO_8859_1).contains(expected)) {
            int n = in.read(buffer);
            if (n == -1) {
                fail("The connection closed before '" + expected + "': " + received.toString(StandardCharsets.ISO_8859_1));
            }
            received.write(buffer, 0, n);
        }
        return received.toString(StandardCharsets.ISO_8859_1);
    }

    /**
     * Read until the end of a chunked response or of the connection.
     */
    static String readToEnd(InputStream in) throws IOException {
        ByteArrayOutputStream received = new ByteArrayOutputStream();
        byte[] buffer = new byte[64 * 1024];
        try {
            int n;
            while ((n = in.read(buffer)) != -1) {
                received.write(buffer, 0, n);
                if (endsWith(received, "\r\n0\r\n\r\n")) {
                    break;
                }
            }
        } catch (SocketTimeoutException e) {
            fail("The response did not end, received " + received.size() + " bytes");
        } catch (IOException e) {
            // connection reset: the response was cut off
        }
        return received.toString(StandardCharsets.ISO_8859_1);
    }

    private static boolean endsWith(ByteArrayOutputStream out, String suffix) {
        if (out.size() < suffix.length()) {
            return false;
        }
        byte[] bytes = out.toByteArray();
        for (int i = 0; i < suffix.length(); i++) {
            if (bytes[bytes.length - suffix.length() + i] != suffix.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    static Socket connect(ServerUnderTest server) throws IOException {
        int port = server.getPort().orElseThrow();
        Socket socket;
        if (server.getApplicationContext().getProperty("micronaut.server.ssl.enabled", Boolean.class).orElse(false)) {
            // HTTP/1.1 over TLS: without ALPN, a server that also speaks HTTP/2 falls back to HTTP/1.1
            socket = trustAll().getSocketFactory().createSocket("localhost", port);
        } else {
            socket = new Socket("localhost", port);
        }
        socket.setSoTimeout(30_000);
        return socket;
    }

    static SSLContext trustAll() throws IOException {
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

    /**
     * What the handlers observed.
     */
    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Recorder {
        final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        final Map<String, CompletableFuture<@Nullable Throwable>> closed = new ConcurrentHashMap<>();
        final CompletableFuture<Throwable> failedSend = new CompletableFuture<>();
        final CompletableFuture<String> closedContext = new CompletableFuture<>();
        final AtomicInteger sent = new AtomicInteger();

        CompletableFuture<@Nullable Throwable> closed(String key) {
            return closed.computeIfAbsent(key, k -> new CompletableFuture<>());
        }

        void record(String key, SseEmitter events) {
            events.onClose(cause -> closed(key).complete(cause));
        }

        @PreDestroy
        void close() {
            scheduler.shutdownNow();
        }
    }

    record Trace(String id) implements PropagatedContextElement {
    }

    static String trace() {
        return PropagatedContext.getOrEmpty().find(Trace.class).map(Trace::id).orElse("none");
    }

    static final class Refused extends RuntimeException {
        Refused() {
            super("refused");
        }
    }

    @ServerFilter("/sse/**")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class TraceFilter {
        @RequestFilter
        void filter(HttpRequest<?> request, MutablePropagatedContext propagatedContext) {
            String trace = request.getHeaders().get(TRACE);
            if (trace != null) {
                propagatedContext.add(new Trace(trace));
            }
        }

        @ResponseFilter
        void response(HttpRequest<?> request, MutableHttpResponse<?> response) {
            if (request.getPath().equals("/sse/filtered")) {
                response.header("X-Server-Filter", "server");
            }
        }
    }

    @Factory
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes {

        @Singleton
        HttpRoutes sseRoutes(Recorder recorder) {
            return routes -> {
                routes.sse("/sse/fields", (request, pathVariables, events) -> {
                    recorder.record("fields", events);
                    events.comment("hello");
                    events.send(Event.of("one").id("1").name("first"));
                    events.send(Event.of("two\nlines").id("2").retry(Duration.ofSeconds(5)));
                    events.send(Event.of(Map.of("k", "v")).comment("json"));
                    events.complete();
                });
                routes.sse("/sse/ordered", (request, pathVariables, events) -> recorder.scheduler.execute(() -> {
                    for (int i = 0; i < 500; i++) {
                        events.send(String.valueOf(i));
                    }
                    events.complete();
                }));
                routes.sse("/sse/resume", (request, pathVariables, events) -> {
                    events.send(events.lastEventId().map(id -> "resumed after " + id).orElse("first connection"));
                    events.complete();
                });
                routes.sse("/sse/heartbeat", (request, pathVariables, events) -> {
                    events.heartbeat(Duration.ofMillis(100));
                    recorder.scheduler.schedule(() -> {
                        events.send("done");
                        events.complete();
                    }, 500, TimeUnit.MILLISECONDS);
                });
                routes.sse("/sse/refuse", (request, pathVariables, events) -> {
                    throw new HttpStatusException(HttpStatus.FORBIDDEN, "forbidden");
                });
                routes.sse("/sse/refuse-later", (request, pathVariables, events) -> {
                    recorder.record("refuse-later", events);
                    recorder.scheduler.schedule(() -> events.fail(new HttpStatusException(HttpStatus.CONFLICT, "conflict")), 50, TimeUnit.MILLISECONDS);
                });
                routes.sse("/sse/refuse-custom", (request, pathVariables, events) -> {
                    throw new Refused();
                });
                routes.error(Refused.class, (request, error) -> HttpResponse.status(HttpStatus.I_AM_A_TEAPOT).body(error.getMessage()));
                routes.sse("/sse/break", (request, pathVariables, events) -> {
                    recorder.record("break", events);
                    events.send("one").thenRun(() -> recorder.scheduler.schedule(() -> events.fail(new IllegalStateException("broken")), 100, TimeUnit.MILLISECONDS));
                });
                routes.sse("/sse/head", (request, pathVariables, events) -> recorder.record("head", events));
                routes.sse("/sse/ticks", (request, pathVariables, events) -> {
                    recorder.record("ticks", events);
                    ScheduledFuture<?>[] ticks = new ScheduledFuture<?>[1];
                    ticks[0] = recorder.scheduler.scheduleAtFixedRate(() -> events.send("tick").whenComplete((ignored, error) -> {
                        if (error != null) {
                            recorder.failedSend.complete(error);
                            ticks[0].cancel(false);
                        }
                    }), 0, 20, TimeUnit.MILLISECONDS);
                });
                routes.sse("/sse/flood", (request, pathVariables, events) -> {
                    recorder.record("flood", events);
                    try (events) {
                        for (int i = 0; i < FLOOD_EVENTS; i++) {
                            events.sendAndAwait(Event.of(KILOBYTE).id(String.valueOf(i)));
                            recorder.sent.incrementAndGet();
                        }
                    }
                }).executeOn(TaskExecutors.BLOCKING);
                routes.sse("/sse/overflow", (request, pathVariables, events) -> {
                    recorder.record("overflow", events);
                    for (int i = 0; i < 1_000_000 && events.isOpen(); i++) {
                        events.send(KILOBYTE);
                    }
                }).executeOn(TaskExecutors.BLOCKING);
                routes.sse("/sse/blocking", (request, pathVariables, events) -> {
                    try (events) {
                        for (int i = 0; i < 100; i++) {
                            events.sendAndAwait(Event.of(String.valueOf(i)));
                        }
                    }
                }).executeOn(TaskExecutors.BLOCKING);
                routes.sse("/sse/context", (request, pathVariables, events) -> contextStream(recorder, events));
                routes.sse("/sse/context-blocking", (request, pathVariables, events) -> contextStream(recorder, events))
                    .executeOn(TaskExecutors.BLOCKING);
                routes.sse("/sse/filtered", (request, pathVariables, events) -> {
                    events.send("filtered");
                    events.complete();
                }).after((request, response) -> response.header("X-Route-Filter", "route"));
                routes.sse("/sse/replaced", (request, pathVariables, events) -> {
                    recorder.record("replaced", events);
                    events.send("original");
                }).afterReplacing((request, response) -> HttpResponse.ok("replaced").contentType(MediaType.TEXT_PLAIN_TYPE));
            };
        }

        private static void contextStream(Recorder recorder, SseEmitter events) {
            // a high-water mark of one byte: the stage completes when the connection took the
            // event, on a thread of the server
            events.highWaterMark(1);
            events.onClose(cause -> recorder.closedContext.complete(trace()));
            events.send("handler:" + trace()).thenRun(() -> {
                events.send("continuation:" + trace());
                events.complete();
            });
        }
    }
}
