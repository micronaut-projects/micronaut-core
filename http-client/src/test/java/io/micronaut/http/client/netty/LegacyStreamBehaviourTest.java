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

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a subscriber of the publisher streams of the Netty client observes, as it did before the
 * streams were read without Reactor (5.3.x at 4ff31b69b8): the event stream of a response that
 * is not declared an event stream, the Netty buffers of dataStream and exchangeStream and their
 * release, the pieces of the body, the limit of the content, and the failure to decode an
 * event.
 */
class LegacyStreamBehaviourTest {
    private static final String SPEC = "LegacyStreamBehaviourTest";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static EmbeddedServer server;
    private static ApplicationContext clientContext;
    private static HttpClient client;
    private static HttpClient limitedClient;

    @BeforeAll
    static void start() {
        server = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", SPEC));
        client = server.getApplicationContext().createBean(HttpClient.class, server.getURL());
        clientContext = ApplicationContext.run(Map.of("micronaut.http.client.max-content-length", 1024));
        limitedClient = clientContext.createBean(HttpClient.class, server.getURL());
    }

    @AfterAll
    static void stop() {
        limitedClient.close();
        clientContext.close();
        client.close();
        server.close();
    }

    /**
     * A server that answers every request with the given content type and body, and closes the
     * connection: the routes of the embedded server answer an event stream request with an event
     * stream or with 406.
     */
    private static <T> T withRawServer(@io.micronaut.core.annotation.Nullable String contentType, String body, java.util.function.Function<HttpClient, T> call) throws Exception {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0, 50, java.net.InetAddress.getLoopbackAddress());
             HttpClient rawClient = HttpClient.create(new java.net.URL("http://127.0.0.1:" + socket.getLocalPort()))) {
            Thread thread = new Thread(() -> {
                while (!socket.isClosed()) {
                    try (java.net.Socket connection = socket.accept()) {
                        java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(connection.getInputStream(), StandardCharsets.ISO_8859_1));
                        String line;
                        while ((line = reader.readLine()) != null && !line.isEmpty()) {
                            // the request headers
                        }
                        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                        String head = "HTTP/1.1 200 OK\r\n"
                            + (contentType == null ? "" : "Content-Type: " + contentType + "\r\n")
                            + "Content-Length: " + bytes.length + "\r\nConnection: close\r\n\r\n";
                        connection.getOutputStream().write(head.getBytes(StandardCharsets.ISO_8859_1));
                        connection.getOutputStream().write(bytes);
                        connection.getOutputStream().flush();
                    } catch (java.io.IOException e) {
                        // closed
                    }
                }
            });
            thread.setDaemon(true);
            thread.start();
            return call.apply(rawClient);
        }
    }

    private static final String TWO_EVENTS = "data: {\"a\":1}\n\ndata: {\"a\":2}\n\n";

    @Test
    void eventStreamOfAJsonResponse() throws Exception {
        List<Event<Map>> events = withRawServer(MediaType.APPLICATION_JSON, TWO_EVENTS, c -> Flux.from(((SseClient) c).eventStream(HttpRequest.GET("/"), Map.class))
            .collectList().block(TIMEOUT));
        assertEquals(2, events.size());
        assertEquals(Map.of("a", 1), events.get(0).getData());
        assertEquals(Map.of("a", 2), events.get(1).getData());
    }

    @Test
    void eventStreamOfAResponseWithoutContentType() throws Exception {
        List<Event<Map>> events = withRawServer(null, TWO_EVENTS, c -> Flux.from(((SseClient) c).eventStream(HttpRequest.GET("/"), Map.class))
            .collectList().block(TIMEOUT));
        assertEquals(2, events.size());
        assertEquals(Map.of("a", 1), events.get(0).getData());
    }

    @Test
    void untypedEventStreamOfAJsonResponse() throws Exception {
        List<String> events = withRawServer(MediaType.APPLICATION_JSON, TWO_EVENTS, c -> Flux.from(((SseClient) c).eventStream(HttpRequest.GET("/")))
            .map(event -> event.getData().toString(StandardCharsets.UTF_8))
            .collectList().block(TIMEOUT));
        assertEquals(List.of("{\"a\":1}", "{\"a\":2}"), events);
    }

    @Test
    void eventStreamOfAJsonResponseOfTheEmbeddedServer() {
        List<Event<Map>> events = Flux.from(((SseClient) client).eventStream(HttpRequest.GET("/legacy-behaviour/sse-as-json"), Map.class))
            .collectList().block(TIMEOUT);
        assertEquals(2, events.size());
        assertEquals(Map.of("a", 1), events.get(0).getData());
        assertEquals(Map.of("a", 2), events.get(1).getData());
    }

    @Test
    void typedEventStreamDecodeFailure() {
        Throwable failure = null;
        try {
            Flux.from(((SseClient) client).eventStream(HttpRequest.GET("/legacy-behaviour/sse-not-json"), Map.class)).blockLast(TIMEOUT);
        } catch (Throwable e) {
            failure = e;
        }
        System.out.println("LEGACY-BEHAVIOUR typedEventStreamDecodeFailure " + describe(failure));
        // the failure of the reader, as it is
        assertInstanceOf(io.micronaut.http.codec.CodecException.class, failure);
    }

    @Test
    void dataStreamBuffersAreNettyBuffersReleasedAfterOnNext() throws Exception {
        List<ByteBuffer<?>> received = new ArrayList<>();
        List<Integer> refCntsInOnNext = new ArrayList<>();
        CompletableFuture<Void> done = new CompletableFuture<>();
        ((StreamingHttpClient) client).dataStream(HttpRequest.GET("/legacy-behaviour/chunks?count=5&size=10&delay=20")).subscribe(new Subscriber<ByteBuffer<?>>() {
            @Override
            public void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer<?> buffer) {
                received.add(buffer);
                refCntsInOnNext.add(((ByteBuf) buffer.asNativeBuffer()).refCnt());
            }

            @Override
            public void onError(Throwable t) {
                done.completeExceptionally(t);
            }

            @Override
            public void onComplete() {
                done.complete(null);
            }
        });
        done.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        assertTrue(!received.isEmpty());
        for (ByteBuffer<?> buffer : received) {
            assertInstanceOf(ByteBuf.class, buffer.asNativeBuffer());
            assertInstanceOf(ReferenceCounted.class, buffer);
            assertEquals(0, ((ByteBuf) buffer.asNativeBuffer()).refCnt(), "released after onNext");
        }
        refCntsInOnNext.forEach(refCnt -> assertTrue(refCnt > 0));
    }

    @Test
    void dataStreamBufferRetainedByTheConsumerIsNotReleased() throws Exception {
        List<ByteBuffer<?>> received = new ArrayList<>();
        CompletableFuture<Void> done = new CompletableFuture<>();
        ((StreamingHttpClient) client).dataStream(HttpRequest.GET("/legacy-behaviour/chunks?count=3&size=10&delay=20")).subscribe(new Subscriber<ByteBuffer<?>>() {
            @Override
            public void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer<?> buffer) {
                ((ReferenceCounted) buffer).retain();
                received.add(buffer);
            }

            @Override
            public void onError(Throwable t) {
                done.completeExceptionally(t);
            }

            @Override
            public void onComplete() {
                done.complete(null);
            }
        });
        done.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        StringBuilder text = new StringBuilder();
        for (ByteBuffer<?> buffer : received) {
            assertEquals(1, ((ByteBuf) buffer.asNativeBuffer()).refCnt(), "retained by the consumer");
            text.append(buffer.toString(StandardCharsets.UTF_8));
            ((ReferenceCounted) buffer).release();
        }
        assertEquals("a".repeat(30), text.toString());
    }

    @Test
    void exchangeStreamBuffersAreNettyBuffersReleasedAfterOnNext() {
        List<HttpResponse<ByteBuffer<?>>> received = Flux.from(((StreamingHttpClient) client).exchangeStream(HttpRequest.GET("/legacy-behaviour/chunks?count=5&size=10&delay=20")))
            .collectList().block(TIMEOUT);
        assertTrue(!received.isEmpty());
        for (HttpResponse<ByteBuffer<?>> response : received) {
            ByteBuffer<?> buffer = response.body();
            assertInstanceOf(ByteBuf.class, buffer.asNativeBuffer());
            assertEquals(0, ((ByteBuf) buffer.asNativeBuffer()).refCnt(), "released after onNext");
        }
    }

    @Test
    void dataStreamPieces() {
        // the first pieces are sent with the headers, the next ones later
        List<Integer> sizes = Flux.from(((StreamingHttpClient) client).dataStream(HttpRequest.GET("/legacy-behaviour/chunks?count=6&size=100&delay=50&eager=3")))
            .map(buffer -> {
                int size = buffer.readableBytes();
                if (buffer instanceof ReferenceCounted counted) {
                    // released by the client after onNext
                    counted.retain();
                    counted.release();
                }
                return size;
            })
            .collectList().block(TIMEOUT);
        System.out.println("LEGACY-BEHAVIOUR dataStreamPieces " + sizes);
        assertEquals(600, sizes.stream().mapToInt(Integer::intValue).sum());
        // a piece that arrives later is an element of its own
        assertEquals(List.of(100, 100, 100), sizes.subList(sizes.size() - 3, sizes.size()));
    }

    @Test
    void dataStreamOfABodyLargerThanTheContentLimit() {
        // a consumer that keeps up
        String outcome;
        try {
            long total = Flux.from(((StreamingHttpClient) limitedClient).dataStream(HttpRequest.GET("/legacy-behaviour/chunks?count=10&size=512&delay=10")))
                .map(ByteBuffer::readableBytes)
                .reduce(0, Integer::sum)
                .block(TIMEOUT);
            outcome = "total=" + total;
        } catch (Throwable e) {
            outcome = describe(e);
        }
        System.out.println("LEGACY-BEHAVIOUR dataStreamOfABodyLargerThanTheContentLimit " + outcome);
        assertEquals("total=5120", outcome);
    }

    @Test
    void dataStreamWithASlowConsumerOfABodyLargerThanTheContentLimit() {
        // the body arrives at once, and the consumer asks for one piece at a time, slowly
        String outcome;
        try {
            long total = Flux.from(((StreamingHttpClient) limitedClient).dataStream(HttpRequest.GET("/legacy-behaviour/chunks?count=10&size=512&delay=0&eager=10")))
                .delayElements(Duration.ofMillis(100), reactor.core.scheduler.Schedulers.single())
                .map(ByteBuffer::readableBytes)
                .reduce(0, Integer::sum)
                .block(TIMEOUT);
            outcome = "total=" + total;
        } catch (Throwable e) {
            outcome = describe(e);
        }
        System.out.println("LEGACY-BEHAVIOUR dataStreamWithASlowConsumerOfABodyLargerThanTheContentLimit " + outcome);
        // the bytes that wait for the consumer are limited
        assertTrue(outcome.startsWith(io.micronaut.http.exceptions.BufferLengthExceededException.class.getName() + ": The content length [5120] exceeds the maximum allowed bufferable length [1024]"), outcome);
    }

    private static String describe(Throwable e) {
        if (e == null) {
            return "none";
        }
        return e.getClass().getName() + ": " + e.getMessage();
    }

    @Requires(property = "spec.name", value = SPEC)
    @Controller("/legacy-behaviour")
    static class BehaviourController {

        @Get(uri = "/sse-as-json", produces = {MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM})
        HttpResponse<String> sseAsJson() {
            // the route accepts an event stream request, and answers JSON
            return HttpResponse.ok(TWO_EVENTS).contentType(MediaType.APPLICATION_JSON_TYPE);
        }

        @Get(uri = "/sse-not-json", produces = MediaType.TEXT_EVENT_STREAM)
        String sseNotJson() {
            return "data: not json\n\n";
        }

        @Get(uri = "/chunks", produces = MediaType.APPLICATION_OCTET_STREAM)
        Publisher<byte[]> chunks(@QueryValue int count, @QueryValue int size, @QueryValue long delay, @QueryValue(defaultValue = "0") int eager) {
            Flux<byte[]> first = Flux.range(0, Math.min(eager, count)).map(i -> "a".repeat(size).getBytes(StandardCharsets.UTF_8));
            Flux<byte[]> rest = Flux.range(0, count - Math.min(eager, count))
                .concatMap(i -> Mono.delay(Duration.ofMillis(delay)).thenReturn("a".repeat(size).getBytes(StandardCharsets.UTF_8)));
            return Flux.concat(first, rest);
        }
    }
}
