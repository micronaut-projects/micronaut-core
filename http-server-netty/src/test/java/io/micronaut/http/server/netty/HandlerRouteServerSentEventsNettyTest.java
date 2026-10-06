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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpVersion;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.netty.DefaultHttpClient;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.sse.Event;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Netty specifics of the server-sent events routes: the event stream is exempt from the
 * compression that applies to other text responses, a blocking send is refused on the event loop,
 * the heartbeat keeps the connection from the idle timeout, and the events reach an HTTP/2 client
 * one by one.
 */
class HandlerRouteServerSentEventsNettyTest {

    private static final String SPEC_NAME = "HandlerRouteServerSentEventsNettyTest";

    @Test
    void theEventStreamIsNotCompressedButOtherTextIs() throws IOException {
        try (ApplicationContext ctx = run(Map.of())) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class);
            // an event stream written as a string body is compressed like any text
            String plain = exchange(server, "GET /netty-sse/plain HTTP/1.1\r\nHost: localhost\r\nAccept-Encoding: gzip\r\nConnection: close\r\n\r\n");
            assertTrue(head(plain).contains("content-encoding: gzip"), plain);
            String sse = exchange(server, "GET /netty-sse/events HTTP/1.1\r\nHost: localhost\r\nAccept-Encoding: gzip\r\nConnection: close\r\n\r\n");
            assertFalse(head(sse).contains("content-encoding"), sse);
            assertTrue(sse.contains("data: " + "e".repeat(2000)), sse);
        }
    }

    @Test
    void blockingSendIsRefusedOnTheEventLoop() throws IOException {
        try (ApplicationContext ctx = run(Map.of())) {
            String response = exchange(ctx.getBean(EmbeddedServer.class), "GET /netty-sse/event-loop HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");
            assertTrue(response.contains("data: refused"), response);
        }
    }

    @Test
    void heartbeatKeepsTheStreamFromTheIdleTimeout() throws IOException {
        try (ApplicationContext ctx = run(Map.of("micronaut.server.idle-timeout", "500ms"))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class);
            String kept = exchange(server, "GET /netty-sse/heartbeat HTTP/1.1\r\nHost: localhost\r\n\r\n");
            assertTrue(kept.contains("data: done"), kept);
            assertTrue(kept.endsWith("0\r\n\r\n"), kept);
            // without the heartbeat, the idle connection is closed before the event
            String closed = exchange(server, "GET /netty-sse/silent HTTP/1.1\r\nHost: localhost\r\n\r\n");
            assertTrue(closed.contains("data: start"), closed);
            assertFalse(closed.contains("data: done"), closed);
        }
    }

    @Test
    void configuredHeartbeatKeepsTheStreamFromTheIdleTimeout() throws IOException {
        try (ApplicationContext ctx = run(Map.of(
            "micronaut.server.idle-timeout", "500ms",
            "micronaut.server.responses.stream.sse-heartbeat", "100ms",
            "micronaut.server.responses.stream.high-water-mark", "16KB"
        ))) {
            HttpServerConfiguration.ResponseStreamConfiguration configuration = ctx.getBean(HttpServerConfiguration.class).getResponseStream();
            assertEquals(Duration.ofMillis(100), configuration.getSseHeartbeat());
            assertEquals(16 * 1024, configuration.getHighWaterMark());
            String kept = exchange(ctx.getBean(EmbeddedServer.class), "GET /netty-sse/configured-heartbeat HTTP/1.1\r\nHost: localhost\r\n\r\n");
            assertTrue(kept.contains(":\n\n"), kept);
            assertTrue(kept.contains("data: done"), kept);
            assertTrue(kept.endsWith("0\r\n\r\n"), kept);
        }
    }

    @Test
    void eventsReachAnHttp2ClientOneByOne() {
        try (ApplicationContext ctx = run(Map.of(
            "micronaut.server.ssl.enabled", true,
            "micronaut.server.ssl.build-self-signed", true,
            "micronaut.server.ssl.port", -1,
            "micronaut.server.http-version", "2.0",
            "micronaut.http.client.http-version", "2.0",
            "micronaut.http.client.ssl.insecure-trust-all-certificates", true
        ));
             // a client created for a URL is not closed with the context: closing it releases its
             // TLS context and the native sessions the server's tickets put in its session cache
             DefaultHttpClient client = ctx.createBean(DefaultHttpClient.class, ctx.getBean(EmbeddedServer.class).getURL())) {
            Recorder recorder = ctx.getBean(Recorder.class);
            // the handler sends the second event only once the client received the first
            List<String> events = Flux.from(client.eventStream(HttpRequest.GET("/netty-sse/steps"), String.class))
                .map(Event::getData)
                .doOnNext(data -> {
                    if (data.equals("one")) {
                        recorder.received.complete(null);
                    }
                })
                .collectList()
                .block(Duration.ofSeconds(20));
            assertEquals(List.of("one", "two"), events);
            assertEquals(HttpVersion.HTTP_2_0, recorder.version.join());
        }
    }

    private static ApplicationContext run(Map<String, Object> properties) {
        Map<String, Object> all = new java.util.HashMap<>(properties);
        all.put("spec.name", SPEC_NAME);
        all.putIfAbsent("micronaut.server.port", -1);
        ApplicationContext ctx = ApplicationContext.run(all);
        ctx.getBean(EmbeddedServer.class).start();
        return ctx;
    }

    private static String head(String response) {
        return response.substring(0, Math.max(0, response.indexOf("\r\n\r\n"))).toLowerCase(java.util.Locale.ROOT);
    }

    private static String exchange(EmbeddedServer server, String request) throws IOException {
        try (Socket socket = new Socket("localhost", server.getPort())) {
            socket.setSoTimeout(20_000);
            OutputStream out = socket.getOutputStream();
            out.write(request.getBytes(StandardCharsets.US_ASCII));
            out.flush();
            InputStream in = socket.getInputStream();
            ByteArrayOutputStream received = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            try {
                int n;
                while ((n = in.read(buffer)) != -1) {
                    received.write(buffer, 0, n);
                    if (received.toString(StandardCharsets.ISO_8859_1).endsWith("\r\n0\r\n\r\n")) {
                        break;
                    }
                }
            } catch (IOException e) {
                // reset: the response ended abruptly
            }
            return received.toString(StandardCharsets.ISO_8859_1);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Recorder {
        final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        final CompletableFuture<Void> received = new CompletableFuture<>();
        final CompletableFuture<HttpVersion> version = new CompletableFuture<>();

        @PreDestroy
        void close() {
            scheduler.shutdownNow();
        }
    }

    @Factory
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes {
        @Singleton
        HttpRoutes nettySseRoutes(Recorder recorder) {
            return routes -> {
                routes.GET("/netty-sse/plain", (request, pathVariables) ->
                    HttpResponse.ok("data: " + "p".repeat(2000) + "\n\n").contentType(MediaType.TEXT_EVENT_STREAM_TYPE));
                routes.GET("/netty-sse/events").sse((request, pathVariables, events) -> {
                    events.send("e".repeat(2000));
                    events.complete();
                });
                routes.GET("/netty-sse/event-loop").sse((request, pathVariables, events) -> {
                    try {
                        events.sendAndAwait(Event.of("blocked"));
                    } catch (IllegalStateException e) {
                        events.send("refused");
                    }
                    events.complete();
                });
                routes.GET("/netty-sse/heartbeat").sse((request, pathVariables, events) -> {
                    events.keepOpen().heartbeat(Duration.ofMillis(100));
                    recorder.scheduler.schedule(() -> {
                        events.send("done");
                        events.complete();
                    }, 1500, TimeUnit.MILLISECONDS);
                });
                routes.GET("/netty-sse/silent").sse((request, pathVariables, events) -> {
                    events.keepOpen().send("start");
                    recorder.scheduler.schedule(() -> {
                        events.send("done");
                        events.complete();
                    }, 1500, TimeUnit.MILLISECONDS);
                });
                routes.GET("/netty-sse/configured-heartbeat").sse((request, pathVariables, events) -> {
                    events.keepOpen();
                    recorder.scheduler.schedule(() -> {
                        events.send("done");
                        events.complete();
                    }, 1500, TimeUnit.MILLISECONDS);
                });
                routes.GET("/netty-sse/steps").sse((request, pathVariables, events) -> {
                    events.keepOpen();
                    recorder.version.complete(request.getHttpVersion());
                    events.send("one");
                    recorder.received.thenRun(() -> {
                        events.send("two");
                        events.complete();
                    });
                });
            };
        }
    }
}
