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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The publisher streams of the Netty client (dataStream, exchangeStream, jsonStream,
 * eventStream, exchangeEventStream) under the reactive operators that cancel or bound the
 * demand: a subscription cancelled before any request, {@code take(1)}, a demand of one element
 * and a server stream that fails in the middle. The subscriber gets no element beyond its demand
 * and no signal after it cancelled, an abandoned body is drained (the server stream completes),
 * the client goes on, and no buffer of the client leaks. The expectations are those of the
 * client before the streams were pushed by the piece reader (5.3.x).
 */
class ReactiveStreamOperatorsTest {
    private static final String SPEC = "ReactiveStreamOperatorsTest";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    static final Map<String, CompletableFuture<String>> SERVER_SIGNALS = new ConcurrentHashMap<>();

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

    enum Operator {
        /**
         * The subscription is cancelled in onSubscribe, before any element is requested.
         */
        CANCEL_BEFORE_REQUEST,
        /**
         * {@code Flux.take(1)}: the first element, then a cancel.
         */
        TAKE_ONE,
        /**
         * A demand of one element, then a cancel a while later.
         */
        REQUEST_ONE,
        /**
         * The server stream fails after two elements.
         */
        ERROR_MID_STREAM
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

    static Stream<Arguments> cases() {
        List<Arguments> cases = new ArrayList<>();
        for (Api api : apis()) {
            for (Operator operator : Operator.values()) {
                cases.add(Arguments.of(api, operator));
            }
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("cases")
    void operator(Api api, Operator operator) throws Exception {
        String id = UUID.randomUUID().toString();
        SERVER_SIGNALS.put(id, new CompletableFuture<>());
        String uri = "/stream-operators/" + api.kind() + "?id=" + id + "&fail=" + (operator == Operator.ERROR_MID_STREAM);
        Publisher<?> publisher = api.open().apply(client, HttpRequest.GET(uri));

        List<Object> elements = new CopyOnWriteArrayList<>();
        List<String> afterCancel = new CopyOnWriteArrayList<>();
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicReference<Subscription> subscription = new AtomicReference<>();
        CompletableFuture<String> terminal = new CompletableFuture<>();
        if (operator == Operator.TAKE_ONE) {
            List<?> taken = Flux.from(publisher).take(1).collectList().block(TIMEOUT);
            elements.addAll(taken);
        } else {
            publisher.subscribe(new Subscriber<Object>() {
                @Override
                public void onSubscribe(Subscription s) {
                    subscription.set(s);
                    if (operator == Operator.CANCEL_BEFORE_REQUEST) {
                        cancelled.set(true);
                        s.cancel();
                    } else if (operator == Operator.REQUEST_ONE) {
                        s.request(1);
                    } else {
                        s.request(Long.MAX_VALUE);
                    }
                }

                @Override
                public void onNext(Object o) {
                    if (cancelled.get()) {
                        afterCancel.add("next " + o);
                    }
                    elements.add(o);
                }

                @Override
                public void onError(Throwable t) {
                    if (cancelled.get()) {
                        afterCancel.add("error " + t);
                    }
                    terminal.complete("error");
                }

                @Override
                public void onComplete() {
                    if (cancelled.get()) {
                        afterCancel.add("complete");
                    }
                    terminal.complete("complete");
                }
            });
        }

        String clientTerminal = null;
        switch (operator) {
            case CANCEL_BEFORE_REQUEST -> {
                await().during(Duration.ofMillis(500)).atMost(TIMEOUT)
                    .untilAsserted(() -> assertEquals(List.of(), elements, "no element without demand"));
            }
            case TAKE_ONE -> assertEquals(1, elements.size());
            case REQUEST_ONE -> {
                await().atMost(TIMEOUT).until(() -> !elements.isEmpty());
                // the server goes on producing: no element beyond the demand
                await().during(Duration.ofMillis(300)).atMost(TIMEOUT)
                    .untilAsserted(() -> assertEquals(1, elements.size(), () -> "elements beyond the demand: " + elements));
                cancelled.set(true);
                subscription.get().cancel();
                await().during(Duration.ofMillis(300)).atMost(TIMEOUT)
                    .untilAsserted(() -> assertEquals(1, elements.size(), () -> "elements after cancel: " + elements));
            }
            case ERROR_MID_STREAM -> {
                clientTerminal = terminal.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            }
        }

        String signal;
        try {
            // cancelled before any request, the server is expected to see nothing: wait less
            signal = SERVER_SIGNALS.get(id).get(operator == Operator.CANCEL_BEFORE_REQUEST ? 1 : 5, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            signal = "none";
        }

        // a later request on the same client still works
        String pong = Mono.from(client.retrieve(HttpRequest.GET("/stream-operators/ping"))).block(TIMEOUT);
        assertEquals("pong", pong);

        System.out.println("STREAM-OPERATORS " + api + " " + operator + " server=" + signal
            + " elements=" + elements.size() + " terminal=" + clientTerminal + " afterCancel=" + afterCancel);
        assertEquals(List.of(), afterCancel, "signals after cancel");
        assertEquals(expectedServerSignal(operator), signal, "what the server observed");
        if (operator == Operator.ERROR_MID_STREAM) {
            assertEquals("error", clientTerminal, "how the subscriber learns of the failure");
        }
        assertNoLeak();
    }

    /**
     * Cancelled after the response started, the client drains the body, so the server stream
     * completes; cancelled before any demand, the request is not sent.
     */
    static String expectedServerSignal(Operator operator) {
        return switch (operator) {
            case CANCEL_BEFORE_REQUEST -> "none";
            case TAKE_ONE, REQUEST_ONE -> "complete";
            case ERROR_MID_STREAM -> "error";
        };
    }

    private static void assertNoLeak() {
        await().during(Duration.ofMillis(60)).pollInterval(Duration.ofMillis(20)).atMost(TIMEOUT)
            .untilAsserted(() -> {
                System.gc();
                ByteBuf probe = ByteBufAllocator.DEFAULT.buffer(16);
                probe.release();
                List<String> reported = leaks.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .filter(m -> m.contains("LEAK") && m.contains("io.micronaut.http.client"))
                    .toList();
                assertTrue(reported.isEmpty(), () -> "leaks: " + reported);
            });
    }

    @Requires(property = "spec.name", value = SPEC)
    @Controller("/stream-operators")
    static class StreamController {
        private static <T> Publisher<T> body(String id, boolean fail, Function<Long, T> map) {
            Flux<Long> ticks = Flux.interval(Duration.ofMillis(20));
            Flux<Long> source = fail
                ? ticks.take(2).concatWith(Mono.error(new IllegalStateException("failed in the middle")))
                : ticks.take(20);
            return source.map(map)
                .doFinally(signal -> SERVER_SIGNALS.get(id).complete(signal.toString().replace("on", "").toLowerCase()));
        }

        @Get(uri = "/data", produces = MediaType.APPLICATION_OCTET_STREAM)
        Publisher<byte[]> data(@QueryValue String id, @QueryValue boolean fail) {
            return body(id, fail, i -> new byte[]{(byte) i.intValue()});
        }

        @Get(uri = "/json", produces = MediaType.APPLICATION_JSON_STREAM)
        Publisher<Map<String, Integer>> json(@QueryValue String id, @QueryValue boolean fail) {
            return body(id, fail, i -> Map.of("i", i.intValue()));
        }

        @Get(uri = "/sse", produces = MediaType.TEXT_EVENT_STREAM)
        Publisher<Event<Integer>> sse(@QueryValue String id, @QueryValue boolean fail) {
            return body(id, fail, i -> Event.of(i.intValue()));
        }

        @Get(uri = "/ping", produces = MediaType.TEXT_PLAIN)
        String ping() {
            return "pong";
        }
    }
}
