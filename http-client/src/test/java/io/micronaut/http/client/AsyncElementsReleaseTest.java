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
package io.micronaut.http.client;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.ClientFilter;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.body.BodyElements;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.sse.Event;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The connection of a streaming exchange is released when nobody reads its elements: the future
 * of a declarative method cancelled before the response arrived, a failed {@code forEach}, a
 * response a filter replaced, and events of a single body closed while it is received.
 */
class AsyncElementsReleaseTest {
    private static final String SPEC = "AsyncElementsReleaseTest";
    private static final long WAIT = 15;

    static volatile CountDownLatch cancelled;
    private static EmbeddedServer server;
    private static HttpClient httpClient;

    @BeforeAll
    static void start() {
        // a read timeout longer than the test: only releasing the connection ends the stream
        server = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", SPEC, "micronaut.http.client.read-timeout", "60s"));
        httpClient = server.getApplicationContext().createBean(HttpClient.class, server.getURL());
    }

    @AfterAll
    static void stop() {
        httpClient.close();
        server.close();
    }

    private static AsyncStreamingHttpClient client() {
        return ((StreamingHttpClient) httpClient).toAsyncStreaming();
    }

    @Test
    void cancellingTheFutureBeforeTheResponseCancelsTheExchange() throws Exception {
        cancelled = new CountDownLatch(1);
        CompletionStage<BodyElements<ByteBuffer<?>>> stage = client().dataStream(HttpRequest.GET("/release-elements/slow"));
        Thread.sleep(100);
        stage.toCompletableFuture().cancel(false);
        assertTrue(cancelled.await(WAIT, TimeUnit.SECONDS), "the server stream was not cancelled");
    }

    @Test
    void cancellingTheFutureOfADeclarativeMethodCancelsTheExchange() throws Exception {
        cancelled = new CountDownLatch(1);
        SlowClient client = server.getApplicationContext().getBean(SlowClient.class);
        CompletionStage<BodyElements<ByteBuffer<?>>> stage = client.slow();
        Thread.sleep(100);
        stage.toCompletableFuture().cancel(false);
        assertTrue(cancelled.await(WAIT, TimeUnit.SECONDS), "the server stream was not cancelled, the elements leaked");
    }

    @Test
    void cancellingTheFutureOfADeclarativeJsonStreamCancelsTheExchange() throws Exception {
        cancelled = new CountDownLatch(1);
        SlowClient client = server.getApplicationContext().getBean(SlowClient.class);
        CompletionStage<BodyElements<Map<String, Object>>> stage = client.slowJson();
        Thread.sleep(100);
        stage.toCompletableFuture().cancel(false);
        assertTrue(cancelled.await(WAIT, TimeUnit.SECONDS), "the server stream was not cancelled, the elements leaked");
    }

    @Test
    void aFailedForEachReleasesTheConnection() throws Exception {
        cancelled = new CountDownLatch(1);
        BodyElements<ByteBuffer<?>> elements = client().dataStream(HttpRequest.GET("/release-elements/slow")).toCompletableFuture().get(WAIT, TimeUnit.SECONDS);
        CompletableFuture<Void> done = elements.forEach(e -> CompletableFuture.failedStage(new IllegalStateException("consumer"))).toCompletableFuture();
        ExecutionException failure = assertThrows(ExecutionException.class, () -> done.get(WAIT, TimeUnit.SECONDS));
        assertEquals("consumer", failure.getCause().getMessage());
        assertTrue(cancelled.await(WAIT, TimeUnit.SECONDS), "the server stream was not cancelled, the connection stays reserved");
        assertEquals(BodyElements.State.FAILED, elements.state());
    }

    @Test
    void aResponseReplacedByAFilterReleasesTheConnection() throws Exception {
        cancelled = new CountDownLatch(1);
        CompletableFuture<BodyElements<ByteBuffer<?>>> stage = client().dataStream(HttpRequest.GET("/release-elements/replaced")).toCompletableFuture();
        ExecutionException failure = assertThrows(ExecutionException.class, () -> stage.get(WAIT, TimeUnit.SECONDS));
        assertInstanceOf(IllegalStateException.class, failure.getCause());
        assertTrue(cancelled.await(WAIT, TimeUnit.SECONDS), "the server stream was not cancelled, the replaced elements leaked");
    }

    @Test
    void closingTheEventOfASingleBodyCancelsIt() throws Exception {
        cancelled = new CountDownLatch(1);
        HttpResponse<BodyElements<Event<String>>> response = client()
            .exchangeEventStream(HttpRequest.GET("/release-elements/slow-text").accept(MediaType.TEXT_PLAIN_TYPE), String.class)
            .toCompletableFuture().get(WAIT, TimeUnit.SECONDS);
        BodyElements<Event<String>> events = response.body();
        // starts receiving the body
        CompletableFuture<Optional<Event<String>>> read = events.next().toCompletableFuture();
        Thread.sleep(100);
        events.close();
        assertThrows(ExecutionException.class, () -> read.get(WAIT, TimeUnit.SECONDS));
        assertTrue(cancelled.await(WAIT, TimeUnit.SECONDS), "the server body was not cancelled");
    }

    @Test
    void aDeclarativeMethodHasNoElementsForNotFound() throws Exception {
        SlowClient client = server.getApplicationContext().getBean(SlowClient.class);
        BodyElements<ByteBuffer<?>> elements = client.missing().toCompletableFuture().get(WAIT, TimeUnit.SECONDS);
        assertEquals(Optional.empty(), elements.next().toCompletableFuture().get(WAIT, TimeUnit.SECONDS));
        HttpResponse<BodyElements<ByteBuffer<?>>> response = client.exchangeMissing().toCompletableFuture().get(WAIT, TimeUnit.SECONDS);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatus());
        assertEquals(Optional.empty(), response.body().next().toCompletableFuture().get(WAIT, TimeUnit.SECONDS));
    }

    private static Flux<byte[]> endless() {
        return Flux.<byte[]>generate(sink -> sink.next(new byte[8192]))
            .delayElements(Duration.ofMillis(20))
            .doFinally(signal -> {
                if (signal == SignalType.CANCEL) {
                    cancelled.countDown();
                }
            });
    }

    @Requires(property = "spec.name", value = SPEC)
    @Client("/release-elements")
    interface SlowClient {
        @Get(value = "/slow", processes = MediaType.APPLICATION_OCTET_STREAM)
        CompletionStage<BodyElements<ByteBuffer<?>>> slow();

        @Get(value = "/slow-json-stream", processes = MediaType.APPLICATION_JSON_STREAM)
        CompletionStage<BodyElements<Map<String, Object>>> slowJson();

        @Get(value = "/missing", processes = MediaType.APPLICATION_OCTET_STREAM)
        CompletionStage<BodyElements<ByteBuffer<?>>> missing();

        @Get(value = "/missing", processes = MediaType.APPLICATION_OCTET_STREAM)
        CompletionStage<HttpResponse<BodyElements<ByteBuffer<?>>>> exchangeMissing();
    }

    @Requires(property = "spec.name", value = SPEC)
    @ClientFilter("/release-elements/replaced")
    static class ReplacingFilter {
        @ResponseFilter
        HttpResponse<?> replace(HttpResponse<?> response) {
            return HttpResponse.ok("replaced");
        }
    }

    @Requires(property = "spec.name", value = SPEC)
    @Controller("/release-elements")
    static class ReleaseController {
        @Get(value = "/slow", produces = MediaType.APPLICATION_OCTET_STREAM)
        CompletableFuture<HttpResponse<Publisher<byte[]>>> slow() {
            // the response arrives after the test cancelled its future
            return CompletableFuture.supplyAsync(() -> HttpResponse.ok(endless()), CompletableFuture.delayedExecutor(500, TimeUnit.MILLISECONDS));
        }

        @Get(value = "/slow-json-stream", produces = MediaType.APPLICATION_JSON_STREAM)
        CompletableFuture<HttpResponse<Publisher<byte[]>>> slowJson() {
            return CompletableFuture.supplyAsync(() -> HttpResponse.ok(endless().map(bytes -> "{\"a\":1}".getBytes())),
                CompletableFuture.delayedExecutor(500, TimeUnit.MILLISECONDS));
        }

        @Get(value = "/replaced", produces = MediaType.APPLICATION_OCTET_STREAM)
        Publisher<byte[]> replaced() {
            return endless();
        }

        @Get(value = "/slow-text", produces = MediaType.TEXT_PLAIN)
        Publisher<byte[]> slowText() {
            return endless().map(bytes -> " ".getBytes());
        }
    }
}
