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
package io.micronaut.http.client.netty;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.StreamingHttpClient;
import io.micronaut.http.client.sse.SseClient;
import io.micronaut.http.sse.Event;
import io.micronaut.runtime.server.EmbeddedServer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.util.ResourceLeakDetector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cancelling the subscription of a publisher stream of the Netty client (dataStream,
 * exchangeStream, jsonStream, eventStream, exchangeEventStream) before the response, after its
 * headers and in the middle of its body: what the server observes (the response stream is
 * cancelled, or completes), whether the connection is reused by the next request, and that no
 * buffer leaks. The expectations were recorded with the client before the streams were read
 * without Reactor (5.3.x at 4ff31b69b8), and the client must keep them.
 */
class LegacyStreamCancellationTest {
    private static final String SPEC = "LegacyStreamCancellationTest";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    static final Map<String, CompletableFuture<String>> SERVER_SIGNALS = new ConcurrentHashMap<>();
    static final Map<String, InetSocketAddress> REMOTES = new ConcurrentHashMap<>();

    private static ResourceLeakDetector.Level previousLevel;
    private static ListAppender<ILoggingEvent> leaks;
    private static EmbeddedServer server;
    private static HttpClient client;

    @BeforeAll
    static void start() {
        previousLevel = ResourceLeakDetector.getLevel();
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID);
        leaks = new ListAppender<>();
        leaks.start();
        ((Logger) LoggerFactory.getLogger(ResourceLeakDetector.class)).addAppender(leaks);
        server = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", SPEC));
        client = server.getApplicationContext().createBean(HttpClient.class, server.getURL());
    }

    @AfterAll
    static void stop() {
        client.close();
        server.close();
        ((Logger) LoggerFactory.getLogger(ResourceLeakDetector.class)).detachAppender(leaks);
        ResourceLeakDetector.setLevel(previousLevel);
    }

    enum When {
        /**
         * The response headers are delayed: the subscription is cancelled before they arrive.
         */
        BEFORE_RESPONSE,
        /**
         * The first piece of the body is delayed: cancelled after the headers.
         */
        AFTER_HEADERS,
        /**
         * Cancelled when the first element arrived.
         */
        MID_BODY
    }

    record Api(String name, String kind, BiFunction<HttpClient, HttpRequest<?>, Publisher<?>> open) {
        @Override
        public String toString() {
            return name;
        }
    }

    static List<Api> apis() {
        return List.of(
            new Api("dataStream", "data", (c, r) -> ((StreamingHttpClient) c).dataStream(r)),
            new Api("exchangeStream", "data", (c, r) -> ((StreamingHttpClient) c).exchangeStream(r)),
            new Api("jsonStream", "json", (c, r) -> ((StreamingHttpClient) c).jsonStream(r, Argument.mapOf(String.class, Integer.class))),
            new Api("eventStream", "sse", (c, r) -> ((SseClient) c).eventStream(r, Argument.of(Integer.class))),
            new Api("exchangeEventStream", "sse", (c, r) -> ((SseClient) c).exchangeEventStream(r, Argument.of(Integer.class), HttpClient.DEFAULT_ERROR_TYPE))
        );
    }

    /**
     * The expectations recorded with the client before this stack: cancelled before the response
     * or after its headers, the connection is closed, so the server stream is cancelled and the
     * connection is not reused; cancelled in the middle of the body, the client reads the rest of
     * the body, so the server stream completes.
     */
    static Stream<Arguments> cases() {
        List<Arguments> cases = new ArrayList<>();
        for (Api api : apis()) {
            for (When when : When.values()) {
                cases.add(Arguments.of(api, when, expectedServerSignal(api, when), expectedReuse(api, when)));
            }
        }
        return cases.stream();
    }

    static String expectedServerSignal(Api api, When when) {
        return when == When.MID_BODY ? "complete" : "cancel";
    }

    static @io.micronaut.core.annotation.Nullable Boolean expectedReuse(Api api, When when) {
        // whether a drained connection is back in the pool when the next request starts depends
        // on timing
        return when == When.MID_BODY ? null : false;
    }

    @ParameterizedTest(name = "{0} cancelled {1}")
    @MethodSource("cases")
    void cancel(Api api, When when, String expectedServerSignal, @io.micronaut.core.annotation.Nullable Boolean expectedReuse) throws Exception {
        String id = UUID.randomUUID().toString();
        SERVER_SIGNALS.put(id, new CompletableFuture<>());
        String uri = "/legacy-cancel/" + api.kind() + "?id=" + id
            + "&headersDelay=" + (when == When.BEFORE_RESPONSE ? 1500 : 0)
            + "&firstDelay=" + (when == When.AFTER_HEADERS ? 1500 : 0);
        HttpRequest<?> request = HttpRequest.GET(uri);
        Publisher<?> publisher = api.open().apply(client, request);

        CompletableFuture<Object> first = new CompletableFuture<>();
        AtomicReference<Subscription> subscription = new AtomicReference<>();
        publisher.subscribe(new Subscriber<Object>() {
            @Override
            public void onSubscribe(Subscription s) {
                subscription.set(s);
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(Object o) {
                if (when == When.MID_BODY && first.complete(o)) {
                    subscription.get().cancel();
                }
            }

            @Override
            public void onError(Throwable t) {
                first.completeExceptionally(t);
            }

            @Override
            public void onComplete() {
                first.complete("complete");
            }
        });
        if (when == When.MID_BODY) {
            first.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } else {
            // the request reaches the server; the response or its first piece is still delayed
            Thread.sleep(400);
            assertTrue(!first.isDone(), () -> "the stream ended before it was cancelled: " + first);
            subscription.get().cancel();
        }

        String signal;
        try {
            signal = SERVER_SIGNALS.get(id).get(5, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            signal = "none";
        }

        // a later request on the same client still works
        String followId = UUID.randomUUID().toString();
        String pong = Mono.from(client.retrieve(HttpRequest.GET("/legacy-cancel/ping?id=" + followId))).block(TIMEOUT);
        assertEquals("pong", pong);
        boolean reused = REMOTES.get(id).equals(REMOTES.get(followId));

        System.out.println("LEGACY-CANCEL " + api + " " + when + " server=" + signal + " reused=" + reused);
        assertEquals(expectedServerSignal, signal, "what the server observed");
        if (expectedReuse != null) {
            assertEquals(expectedReuse, reused, "whether the connection was reused");
        }
        assertNoLeak();
    }

    private static void assertNoLeak() throws InterruptedException {
        for (int i = 0; i < 3; i++) {
            System.gc();
            // a leak is reported when a buffer is tracked
            ByteBuf probe = ByteBufAllocator.DEFAULT.buffer(16);
            probe.release();
            Thread.sleep(20);
        }
        // the leaks of the client: the server has leaks of its own when a response stream is
        // cancelled, which are not what this test is about
        List<String> reported = leaks.list.stream().map(ILoggingEvent::getFormattedMessage)
            .filter(m -> m.contains("LEAK") && m.contains("io.micronaut.http.client"))
            .toList();
        assertTrue(reported.isEmpty(), () -> "leaks: " + reported);
    }

    @Requires(property = "spec.name", value = SPEC)
    @Controller("/legacy-cancel")
    static class StreamController {
        private static <T> Publisher<T> body(String id, long headersDelay, long firstDelay, Flux<T> elements) {
            Flux<T> body = Flux.concat(Mono.delay(Duration.ofMillis(firstDelay)).then(Mono.empty()), elements)
                .doFinally(signal -> SERVER_SIGNALS.get(id).complete(signal.toString().replace("on", "").toLowerCase()));
            if (headersDelay == 0) {
                return body;
            }
            return Mono.delay(Duration.ofMillis(headersDelay))
                .doOnCancel(() -> SERVER_SIGNALS.get(id).complete("cancel"))
                .thenMany(body);
        }

        private static void remote(String id, HttpRequest<?> request) {
            REMOTES.put(id, request.getRemoteAddress());
        }

        @Get(uri = "/data", produces = MediaType.APPLICATION_OCTET_STREAM)
        Publisher<byte[]> data(@QueryValue String id, @QueryValue long headersDelay, @QueryValue long firstDelay, HttpRequest<?> request) {
            remote(id, request);
            return body(id, headersDelay, firstDelay, Flux.interval(Duration.ofMillis(20)).take(20).map(i -> new byte[]{(byte) i.intValue()}));
        }

        @Get(uri = "/json", produces = MediaType.APPLICATION_JSON_STREAM)
        Publisher<Map<String, Integer>> json(@QueryValue String id, @QueryValue long headersDelay, @QueryValue long firstDelay, HttpRequest<?> request) {
            remote(id, request);
            return body(id, headersDelay, firstDelay, Flux.interval(Duration.ofMillis(20)).take(20).map(i -> Map.of("i", i.intValue())));
        }

        @Get(uri = "/sse", produces = MediaType.TEXT_EVENT_STREAM)
        Publisher<Event<Integer>> sse(@QueryValue String id, @QueryValue long headersDelay, @QueryValue long firstDelay, HttpRequest<?> request) {
            remote(id, request);
            return body(id, headersDelay, firstDelay, Flux.interval(Duration.ofMillis(20)).take(20).map(i -> Event.of(i.intValue())));
        }

        @Get(uri = "/ping", produces = MediaType.TEXT_PLAIN)
        String ping(@QueryValue String id, HttpRequest<?> request, @Header(value = "X-Unused", defaultValue = "") String unused) {
            remote(id, request);
            return "pong";
        }
    }
}
