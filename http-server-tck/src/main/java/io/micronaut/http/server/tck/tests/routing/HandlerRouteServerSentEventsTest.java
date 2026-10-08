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
import io.micronaut.http.exceptions.StreamOverflowException;
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
 * routes), the stream ends when the handler returns unless it keeps it open, a slow client
 * pauses the sender, a disconnect closes the emitter, and the stream is never compressed.
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
            assertInstanceOf(StreamOverflowException.class, closed);
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

    @Test
    void streamEndsWhenTheHandlerReturns() throws Exception {
        try (ServerUnderTest server = server()) {
            assertEquals("data: one\n\n", server.exchange(HttpRequest.GET("/sse/returned"), String.class).body());
            assertNull(recorder(server).closed("returned").get(20, TimeUnit.SECONDS));
        }
    }

    @Test
    void blockingHandlerFailureBeforeTheFirstEventIsAnsweredByTheErrorRoutes() throws Exception {
        try (ServerUnderTest server = server()) {
            assertStatus(server, "/sse/blocking-refuse", HttpStatus.NOT_FOUND);
            assertInstanceOf(HttpStatusException.class, recorder(server).closed("blocking-refuse").get(20, TimeUnit.SECONDS));
        }
    }

    @Test
    void blockingHandlerFailureAfterTheFirstEventEndsTheStreamAbruptly() throws Exception {
        try (ServerUnderTest server = server();
             Socket socket = connect(server)) {
            request(socket, "GET /sse/blocking-break HTTP/1.1\r\nHost: localhost\r\n\r\n");
            String received = readToEnd(socket.getInputStream());
            assertTrue(received.contains("data: one"), received);
            assertFalse(received.endsWith("0\r\n\r\n"), "A failed stream must not end like a complete chunked body: " + received);
            assertEquals("database went away", recorder(server).closed("blocking-break").get(20, TimeUnit.SECONDS).getMessage());
        }
    }

    @Test
    void keepOpenAfterTheHandlerReturnedFails() throws Exception {
        try (ServerUnderTest server = server()) {
            assertEquals("", server.exchange(HttpRequest.GET("/sse/late-keep-open"), String.class).getBody(String.class).orElse(""));
            assertTrue(recorder(server).lateKeepOpen.get(20, TimeUnit.SECONDS).getMessage().contains("before the handler returns"));
        }
    }

    @Test
    void headersAreAddedBeforeTheFirstEvent() throws Exception {
        try (ServerUnderTest server = server()) {
            HttpResponse<String> response = server.exchange(HttpRequest.GET("/sse/headers"), String.class);
            assertEquals("no", response.getHeaders().get("X-Accel-Buffering"));
            assertEquals("no-store", response.getHeaders().get(HttpHeaders.CACHE_CONTROL));
            assertNull(response.getHeaders().get("X-Late"));
            assertEquals("data: one\n\n", response.body());
            assertTrue(recorder(server).lateHeader.get(20, TimeUnit.SECONDS).getMessage().contains("already sent"));
        }
    }

    @Test
    void postedBodyIsStreamedBack() throws Exception {
        try (ServerUnderTest server = server()) {
            HttpResponse<String> response = server.exchange(HttpRequest.POST("/sse/words", "a b c").contentType(MediaType.TEXT_PLAIN_TYPE), String.class);
            assertEquals("data: a\n\ndata: b\n\ndata: c\n\n", response.body());
        }
    }

    @Test
    void bodyIsReadWhileEventsAreSent() throws Exception {
        try (ServerUnderTest server = server()) {
            HttpResponse<String> response = server.exchange(HttpRequest.POST("/sse/elements", "[1,2]").contentType(MediaType.APPLICATION_JSON_TYPE), String.class);
            assertEquals("data: start\n\ndata: 1\n\ndata: 2\n\n", response.body());
            assertNull(recorder(server).closed("elements").get(20, TimeUnit.SECONDS));
        }
    }

    @Test
    void handlerAnswersWithAnotherResponseBeforeTheFirstEvent() throws Exception {
        try (ServerUnderTest server = server()) {
            // a notification: 202 without a body
            HttpResponse<String> accepted = server.exchange(message("notify"), String.class);
            assertEquals(HttpStatus.ACCEPTED, accepted.getStatus());
            assertEquals("abc-123", accepted.getHeaders().get("Mcp-Session-Id"));
            assertEquals("", accepted.getBody(String.class).orElse(""));
            // a single JSON message
            HttpResponse<String> json = server.exchange(message("ping"), String.class);
            assertEquals(HttpStatus.OK, json.getStatus());
            assertTrue(json.getHeaders().get(HttpHeaders.CONTENT_TYPE).startsWith(MediaType.APPLICATION_JSON), json.getHeaders().get(HttpHeaders.CONTENT_TYPE));
            assertEquals("abc-123", json.getHeaders().get("Mcp-Session-Id"));
            assertEquals("{\"result\":\"pong\"}", json.body());
            // an event stream
            HttpResponse<String> stream = server.exchange(message("work"), String.class);
            assertTrue(stream.getHeaders().get(HttpHeaders.CONTENT_TYPE).startsWith(MediaType.TEXT_EVENT_STREAM));
            assertEquals("abc-123", stream.getHeaders().get("Mcp-Session-Id"));
            assertEquals("data: progress\n\ndata: done\n\n", stream.body());
        }
    }

    @Test
    void anotherResponseForAClientThatAcceptsOnlyIt() throws Exception {
        try (ServerUnderTest server = server()) {
            // the route produces JSON too: a client that accepts only JSON reaches the handler
            HttpResponse<String> json = server.exchange(HttpRequest.POST("/sse/messages", "ping")
                .contentType(MediaType.TEXT_PLAIN_TYPE)
                .accept(MediaType.APPLICATION_JSON_TYPE), String.class);
            assertEquals(HttpStatus.OK, json.getStatus());
            assertEquals("{\"result\":\"pong\"}", json.body());
        }
    }

    @Test
    void sendAfterTheHandlerReturnedWithoutKeepOpenFails() throws Exception {
        try (ServerUnderTest server = server()) {
            assertEquals("", server.exchange(HttpRequest.GET("/sse/forgot-keep-open"), String.class).getBody(String.class).orElse(""));
            Throwable late = recorder(server).lateSend.get(20, TimeUnit.SECONDS);
            assertTrue(late.getMessage().contains("call keepOpen()"), late.getMessage());
        }
    }

    @Test
    void anotherResponseAfterTheFirstEventFails() throws Exception {
        try (ServerUnderTest server = server()) {
            assertEquals("data: one\n\n", server.exchange(HttpRequest.GET("/sse/late-respond"), String.class).body());
            assertTrue(recorder(server).lateRespond.get(20, TimeUnit.SECONDS).getMessage().contains("already sent"));
        }
    }

    private static HttpRequest<String> message(String message) {
        return HttpRequest.POST("/sse/messages", message)
            .contentType(MediaType.TEXT_PLAIN_TYPE)
            .accept(MediaType.APPLICATION_JSON_TYPE, MediaType.TEXT_EVENT_STREAM_TYPE);
    }

    @Test
    void failureAfterTheStreamCompletedHasNoEffect() throws Exception {
        try (ServerUnderTest server = server()) {
            HttpResponse<String> response = server.exchange(HttpRequest.GET("/sse/fail-on-close"), String.class);
            assertEquals(HttpStatus.OK, response.getStatus());
            assertEquals("", response.getBody(String.class).orElse(""));
        }
    }

    @Test
    void submittedFormIsStreamedBack() throws Exception {
        try (ServerUnderTest server = server()) {
            HttpResponse<String> response = server.exchange(HttpRequest.POST("/sse/form", "name=Fred").contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE), String.class);
            assertEquals("data: hello Fred\n\n", response.body());
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
            TrustManager trustAll = new X509TrustManager() {
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
            };
            context.init(null, new TrustManager[]{trustAll}, null);
            return context;
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }
    }

    static String trace() {
        return PropagatedContext.getOrEmpty().find(Trace.class).map(Trace::id).orElse("none");
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
        final CompletableFuture<IllegalStateException> lateKeepOpen = new CompletableFuture<>();
        final CompletableFuture<IllegalStateException> lateHeader = new CompletableFuture<>();
        final CompletableFuture<IllegalStateException> lateRespond = new CompletableFuture<>();
        final CompletableFuture<Throwable> lateSend = new CompletableFuture<>();
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
                routes.GET("/sse/fields").sse((request, pathVariables, events) -> {
                    recorder.record("fields", events);
                    events.comment("hello");
                    events.send(Event.of("one").id("1").name("first"));
                    events.send(Event.of("two\nlines").id("2").retry(Duration.ofSeconds(5)));
                    events.send(Event.of(Map.of("k", "v")).comment("json"));
                    events.complete();
                });
                routes.GET("/sse/ordered").sse((request, pathVariables, events) -> {
                    events.keepOpen();
                    recorder.scheduler.execute(() -> {
                        for (int i = 0; i < 500; i++) {
                            events.send(String.valueOf(i));
                        }
                        events.complete();
                    });
                });
                routes.GET("/sse/resume").sse((request, pathVariables, events) -> {
                    events.send(events.lastEventId().map(id -> "resumed after " + id).orElse("first connection"));
                    events.complete();
                });
                routes.GET("/sse/heartbeat").sse((request, pathVariables, events) -> {
                    events.keepOpen().heartbeat(Duration.ofMillis(100));
                    recorder.scheduler.schedule(() -> {
                        events.send("done");
                        events.complete();
                    }, 500, TimeUnit.MILLISECONDS);
                });
                routes.GET("/sse/refuse").sse((request, pathVariables, events) -> {
                    throw new HttpStatusException(HttpStatus.FORBIDDEN, "forbidden");
                });
                routes.GET("/sse/refuse-later").sse((request, pathVariables, events) -> {
                    recorder.record("refuse-later", events.keepOpen());
                    recorder.scheduler.schedule(() -> events.fail(new HttpStatusException(HttpStatus.CONFLICT, "conflict")), 50, TimeUnit.MILLISECONDS);
                });
                routes.GET("/sse/refuse-custom").sse((request, pathVariables, events) -> {
                    throw new Refused();
                });
                routes.error(Refused.class, (request, error) -> HttpResponse.status(HttpStatus.I_AM_A_TEAPOT).body(error.getMessage()));
                routes.GET("/sse/break").sse((request, pathVariables, events) -> {
                    recorder.record("break", events.keepOpen());
                    events.send("one").thenRun(() -> recorder.scheduler.schedule(() -> events.fail(new IllegalStateException("broken")), 100, TimeUnit.MILLISECONDS));
                });
                routes.GET("/sse/head").sse((request, pathVariables, events) -> recorder.record("head", events));
                routes.GET("/sse/ticks").sse((request, pathVariables, events) -> {
                    recorder.record("ticks", events.keepOpen());
                    ScheduledFuture<?>[] ticks = new ScheduledFuture<?>[1];
                    ticks[0] = recorder.scheduler.scheduleAtFixedRate(() -> events.send("tick").whenComplete((ignored, error) -> {
                        if (error != null) {
                            recorder.failedSend.complete(error);
                            ticks[0].cancel(false);
                        }
                    }), 0, 20, TimeUnit.MILLISECONDS);
                });
                routes.GET("/sse/flood").executeOn(TaskExecutors.BLOCKING).sse((request, pathVariables, events) -> {
                    recorder.record("flood", events);
                    for (int i = 0; i < FLOOD_EVENTS; i++) {
                        events.send(Event.of(KILOBYTE).id(String.valueOf(i))).toCompletableFuture().join();
                        recorder.sent.incrementAndGet();
                    }
                });
                routes.GET("/sse/overflow").executeOn(TaskExecutors.BLOCKING).sse((request, pathVariables, events) -> {
                    recorder.record("overflow", events);
                    for (int i = 0; i < 1_000_000 && events.isOpen(); i++) {
                        events.send(KILOBYTE);
                    }
                });
                routes.GET("/sse/blocking").executeOn(TaskExecutors.BLOCKING).sse((request, pathVariables, events) -> {
                    for (int i = 0; i < 100; i++) {
                        events.send(String.valueOf(i)).toCompletableFuture().join();
                    }
                });
                routes.GET("/sse/context").sse((request, pathVariables, events) -> contextStream(recorder, events));
                routes.GET("/sse/context-blocking").executeOn(TaskExecutors.BLOCKING)
                    .sse((request, pathVariables, events) -> contextStream(recorder, events));
                routes.GET("/sse/filtered").after((request, response) -> response.header("X-Route-Filter", "route")).and().sse((request, pathVariables, events) -> {
                    events.send("filtered");
                    events.complete();
                });
                routes.GET("/sse/replaced")
                    .afterReplacing((request, response) -> HttpResponse.ok("replaced").contentType(MediaType.TEXT_PLAIN_TYPE)).and()
                    .sse((request, pathVariables, events) -> {
                    recorder.record("replaced", events.keepOpen());
                    events.send("original");
                });
                routes.GET("/sse/returned").sse((request, pathVariables, events) -> {
                    recorder.record("returned", events);
                    events.send("one");
                });
                routes.GET("/sse/blocking-refuse").executeOn(TaskExecutors.BLOCKING).sse((request, pathVariables, events) -> {
                    recorder.record("blocking-refuse", events);
                    throw new HttpStatusException(HttpStatus.NOT_FOUND, "no such job");
                });
                routes.GET("/sse/blocking-break").executeOn(TaskExecutors.BLOCKING).sse((request, pathVariables, events) -> {
                    recorder.record("blocking-break", events);
                    // a high-water mark of one byte: the send returns once the connection took the
                    // event, so the failure does not overtake it
                    events.highWaterMark(1).send("one").toCompletableFuture().join();
                    throw new IllegalStateException("database went away");
                });
                routes.GET("/sse/late-keep-open").sse((request, pathVariables, events) ->
                    recorder.scheduler.schedule(() -> {
                        try {
                            events.keepOpen();
                        } catch (IllegalStateException e) {
                            recorder.lateKeepOpen.complete(e);
                        }
                    }, 50, TimeUnit.MILLISECONDS));
                routes.GET("/sse/headers").sse((request, pathVariables, events) -> {
                    events.header("X-Accel-Buffering", "no").header(HttpHeaders.CACHE_CONTROL, "no-store");
                    events.send("one");
                    try {
                        events.header("X-Late", "late");
                    } catch (IllegalStateException e) {
                        recorder.lateHeader.complete(e);
                    }
                });
                routes.POST("/sse/words").consumes(MediaType.TEXT_PLAIN_TYPE).body(String.class).sse((request, pathVariables, text, events) -> {
                    for (String word : text.split(" ")) {
                        events.send(word);
                    }
                });
                routes.POST("/sse/elements").body().sse((request, pathVariables, body, events) -> {
                    recorder.record("elements", events.keepOpen());
                    // the response is sent before the body is read: the body stays readable
                    events.send("start").thenCompose(sent -> body.elements(Integer.class).forEach(events::send))
                        .whenComplete((done, error) -> {
                            if (error == null) {
                                events.complete();
                            } else {
                                events.fail(error);
                            }
                        });
                });
                routes.POST("/sse/messages").consumes(MediaType.TEXT_PLAIN_TYPE)
                    .produces(MediaType.TEXT_EVENT_STREAM_TYPE, MediaType.APPLICATION_JSON_TYPE)
                    .body(String.class).sse((request, pathVariables, message, events) -> {
                    events.header("Mcp-Session-Id", "abc-123");
                    switch (message) {
                        case "notify" -> events.respond(HttpResponse.accepted());
                        case "ping" -> events.respond(HttpResponse.ok(Map.of("result", "pong")));
                        default -> {
                            events.send("progress");
                            events.send("done");
                        }
                    }
                });
                routes.GET("/sse/forgot-keep-open").sse((request, pathVariables, events) ->
                    recorder.scheduler.schedule(() -> events.send("late").whenComplete((ignored, error) -> {
                        if (error != null) {
                            recorder.lateSend.complete(error);
                        }
                    }), 50, TimeUnit.MILLISECONDS));
                routes.GET("/sse/late-respond").sse((request, pathVariables, events) -> {
                    events.send("one");
                    try {
                        events.respond(HttpResponse.ok("never"));
                    } catch (IllegalStateException e) {
                        recorder.lateRespond.complete(e);
                    }
                });
                routes.GET("/sse/fail-on-close").sse((request, pathVariables, events) ->
                    events.onClose(cause -> events.fail(new HttpStatusException(HttpStatus.CONFLICT, "late failure"))));
                routes.POST("/sse/form").form().sse((request, pathVariables, form, events) -> events.send("hello " + form.getString("name")));
            };
        }

        private static void contextStream(Recorder recorder, SseEmitter events) {
            // a high-water mark of one byte: the stage completes when the connection took the
            // event, on a thread of the server
            events.keepOpen().highWaterMark(1);
            events.onClose(cause -> recorder.closedContext.complete(trace()));
            events.send("handler:" + trace()).thenRun(() -> {
                events.send("continuation:" + trace());
                events.complete();
            });
        }
    }
}
