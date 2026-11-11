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
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.sse.Event;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A route on the blocking executor whose publisher emits the response, and the first event of a
 * streamed body, during the subscription and then keeps the thread, has them written at once,
 * not when the subscription returns. This holds without a filter, with a filter method, and with
 * a filter that subscribes to the response publisher itself.
 */
class BlockedRouteThreadStreamingTest {
    private static final String SPEC_NAME = "BlockedRouteThreadStreamingTest";
    private static final int ROUTE_WAIT_SECONDS = 10;
    private static final int READ_TIMEOUT_MILLIS = 5_000;

    private static ApplicationContext ctx;
    private static EmbeddedServer server;

    @BeforeAll
    static void start() {
        ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.port", -1));
        server = ctx.getBean(EmbeddedServer.class).start();
    }

    @AfterAll
    static void stop() {
        if (ctx != null) {
            ctx.close();
        }
    }

    static Stream<Arguments> streams() {
        return Stream.of("none", "request-filter", "reactive-filter").flatMap(filter -> Stream.of(
            Arguments.of("GET", "/blocked-thread/" + filter + "/stream"),
            Arguments.of("GET", "/blocked-thread/" + filter + "/any-stream"),
            Arguments.of("POST", "/blocked-thread/" + filter + "/any-stream")
        ));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("streams")
    void theFirstEventIsWrittenWhileTheRouteThreadIsBlocked(String method, String path) throws IOException, InterruptedException {
        Routes routes = ctx.getBean(Routes.class);
        Block block = new Block();
        routes.block.set(block);
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.getPort())) {
            socket.setSoTimeout(READ_TIMEOUT_MILLIS);
            send(socket, method, path);
            InputStream in = socket.getInputStream();
            ByteArrayOutputStream received = new ByteArrayOutputStream();

            readUntil(in, received, "data: first");
            String head = received.toString(StandardCharsets.ISO_8859_1);
            assertTrue(head.startsWith("HTTP/1.1 200"), head);
            assertTrue(head.toLowerCase().contains("content-type: text/event-stream"), head);
            assertStillBlocked(block);

            block.release.countDown();
            readUntil(in, received, "data: second");
        } finally {
            block.release.countDown();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"none", "request-filter", "reactive-filter"})
    void aSingleBodyIsWrittenWhileTheRouteThreadIsBlocked(String filter) throws IOException, InterruptedException {
        Routes routes = ctx.getBean(Routes.class);
        Block block = new Block();
        routes.block.set(block);
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.getPort())) {
            socket.setSoTimeout(READ_TIMEOUT_MILLIS);
            send(socket, "GET", "/blocked-thread/" + filter + "/single");
            InputStream in = socket.getInputStream();
            ByteArrayOutputStream received = new ByteArrayOutputStream();

            readUntil(in, received, "\r\n\r\nthe body");
            String response = received.toString(StandardCharsets.ISO_8859_1);
            assertTrue(response.startsWith("HTTP/1.1 200"), response);
            assertStillBlocked(block);
        } finally {
            block.release.countDown();
        }
    }

    /**
     * The route emits before it starts to block, so what it emitted can be read first. Wait for the blocking to start,
     * then check the route thread has not been released: what was read was written while the thread was kept.
     *
     * @param block The state of the request
     */
    private static void assertStillBlocked(Block block) throws InterruptedException {
        assertTrue(block.started.await(ROUTE_WAIT_SECONDS, TimeUnit.SECONDS), "The route thread never started to block");
        assertTrue(block.blocked, "The route thread should still be blocked");
    }

    private static void send(Socket socket, String method, String path) throws IOException {
        String body = method.equals("POST") ? "{\"jsonrpc\":\"2.0\"}" : "";
        String headers = method.equals("POST") ? "Content-Type: application/json\r\nContent-Length: " + body.length() + "\r\n" : "";
        String request = method + " " + path + " HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n" + headers + "\r\n" + body;
        socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
    }

    private static void readUntil(InputStream in, ByteArrayOutputStream received, String marker) throws IOException {
        byte[] buffer = new byte[1024];
        while (!received.toString(StandardCharsets.ISO_8859_1).contains(marker)) {
            int n;
            try {
                n = in.read(buffer);
            } catch (SocketTimeoutException e) {
                fail("Nothing written for " + READ_TIMEOUT_MILLIS + " ms while waiting for '" + marker + "', got: " + received.toString(StandardCharsets.ISO_8859_1));
                return;
            }
            if (n == -1) {
                fail("The connection ended before '" + marker + "', got: " + received.toString(StandardCharsets.ISO_8859_1));
            }
            received.write(buffer, 0, n);
        }
    }

    /**
     * What one request blocks on, so that a route thread of an earlier case cannot change what a later case reads.
     */
    static final class Block {
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch started = new CountDownLatch(1);
        volatile boolean blocked;
    }

    @Controller("/blocked-thread")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes {
        final AtomicReference<Block> block = new AtomicReference<>(released());

        @ExecuteOn(TaskExecutors.BLOCKING)
        @Get(uris = {"/none/stream", "/request-filter/stream", "/reactive-filter/stream"}, produces = MediaType.TEXT_EVENT_STREAM)
        Mono<HttpResponse<Publisher<Event<String>>>> stream() {
            return emitFirstEventThenBlock();
        }

        // the type of the body is only known at runtime, like an endpoint answering with JSON or an event stream
        @ExecuteOn(TaskExecutors.BLOCKING)
        @Get(uris = {"/none/any-stream", "/request-filter/any-stream", "/reactive-filter/any-stream"}, produces = MediaType.TEXT_EVENT_STREAM)
        Mono<HttpResponse<?>> anyStream() {
            return emitFirstEventThenBlock().map(response -> response);
        }

        @ExecuteOn(TaskExecutors.BLOCKING)
        @Post(uris = {"/none/any-stream", "/request-filter/any-stream", "/reactive-filter/any-stream"}, produces = MediaType.TEXT_EVENT_STREAM)
        Mono<HttpResponse<?>> postAnyStream(@Body Map<String, Object> message) {
            return emitFirstEventThenBlock().map(response -> response);
        }

        @ExecuteOn(TaskExecutors.BLOCKING)
        @Get(uris = {"/none/single", "/request-filter/single", "/reactive-filter/single"}, produces = MediaType.TEXT_PLAIN)
        HttpResponse<Mono<String>> single() {
            Block current = block.get();
            return HttpResponse.ok(Mono.create(sink -> {
                sink.success("the body");
                block(current);
            }));
        }

        private Mono<HttpResponse<Publisher<Event<String>>>> emitFirstEventThenBlock() {
            Block current = block.get();
            return Mono.create(sink -> {
                Sinks.Many<Event<String>> events = Sinks.many().unicast().onBackpressureBuffer();
                sink.success(HttpResponse.ok(events.asFlux()));
                events.emitNext(Event.of("first"), Sinks.EmitFailureHandler.FAIL_FAST);
                boolean released = block(current);
                events.emitNext(Event.of(released ? "second" : "not released"), Sinks.EmitFailureHandler.FAIL_FAST);
                events.emitComplete(Sinks.EmitFailureHandler.FAIL_FAST);
            });
        }

        /**
         * Keep the thread until the test has read what was emitted before.
         *
         * @param current The state of the request, whose latch the test releases
         * @return Whether the test released the latch
         */
        private boolean block(Block current) {
            current.blocked = true;
            current.started.countDown();
            try {
                return current.release.await(ROUTE_WAIT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } finally {
                current.blocked = false;
            }
        }

        private static Block released() {
            Block released = new Block();
            released.release.countDown();
            return released;
        }
    }

    @ServerFilter("/blocked-thread/request-filter/**")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class PassingRequestFilter {
        @RequestFilter
        void pass(HttpRequest<?> request) {
            // lets every request through
        }
    }

    @Filter("/blocked-thread/reactive-filter/**")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class PassingReactiveFilter implements HttpServerFilter {
        @Override
        public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            return chain.proceed(request);
        }
    }
}
