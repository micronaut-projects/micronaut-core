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
package io.micronaut.http.client.tck.tests;

import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * What cancelling a {@link RawHttpClient} exchange does, observed on the wire of a raw upstream:
 * cancelling the subscription to the exchange publisher.
 */
@SuppressWarnings({
    "java:S2259", // The tests will show if it's null
    "java:S5960", // We're allowed assertions, as these are used in tests only
})
class RawClientCancellationTest {
    static final String SPEC_NAME = "RawClientCancellationTest";

    private static final long TIMEOUT_SECONDS = 10;

    @ParameterizedTest
    @EnumSource(Api.class)
    void cancelBeforeTheResponseAbortsTheRequest(Api api) throws Exception {
        try (ServerUnderTest server = server();
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class);
             RawUpstream upstream = new RawUpstream()) {
            TrackedBody body = new TrackedBody(Flux.just("hello".getBytes(StandardCharsets.UTF_8)));
            Exchange exchange = api.start(client, HttpRequest.POST(upstream.uri("/cancel"), null), body.body);
            RawUpstream.Connection connection = upstream.nextConnection(TIMEOUT_SECONDS);
            Assertions.assertNotNull(connection);
            Assertions.assertTrue(connection.awaitRequest(TIMEOUT_SECONDS));

            Assertions.assertTrue(exchange.cancel());

            // the request is aborted: the connection is closed, and the request body released
            Assertions.assertTrue(connection.awaitClosed(TIMEOUT_SECONDS), "The connection was not closed");
            await(body::released, "The request body was not released: " + body);
            // a response that arrives anyway is not delivered
            writeQuietly(connection, "HTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\nhello");
            Thread.sleep(200);
            Assertions.assertNull(exchange.response());
        }
    }

    @ParameterizedTest
    @EnumSource(Api.class)
    void cancelWhileTheRequestBodyStreamsStopsIt(Api api) throws Exception {
        try (ServerUnderTest server = server();
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class);
             RawUpstream upstream = new RawUpstream()) {
            TrackedBody body = new TrackedBody(Flux.interval(Duration.ofMillis(20)).map(i -> new byte[1024]));
            Exchange exchange = api.start(client, HttpRequest.POST(upstream.uri("/streaming"), null), body.body);
            RawUpstream.Connection connection = upstream.nextConnection(TIMEOUT_SECONDS);
            Assertions.assertNotNull(connection);
            await(() -> connection.bytesReceived.get() > 8 * 1024, "The request body was not streamed");

            Assertions.assertTrue(exchange.cancel());

            Assertions.assertTrue(connection.awaitClosed(TIMEOUT_SECONDS), "The connection was not closed");
            await(() -> body.cancelled.get() && body.discarded.get(), "The request body was not cancelled: " + body);
            long received = connection.bytesReceived.get();
            Thread.sleep(200);
            Assertions.assertEquals(received, connection.bytesReceived.get(), "The request body is still sent");
            Assertions.assertNull(exchange.response());
        }
    }

    @ParameterizedTest
    @EnumSource(Api.class)
    void cancelAfterTheResponseHasNoEffect(Api api) throws Exception {
        try (ServerUnderTest server = server();
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class);
             RawUpstream upstream = new RawUpstream()) {
            Exchange exchange = api.start(client, HttpRequest.GET(upstream.uri("/after")), null);
            RawUpstream.Connection connection = upstream.nextConnection(TIMEOUT_SECONDS);
            Assertions.assertNotNull(connection);
            Assertions.assertTrue(connection.awaitRequest(TIMEOUT_SECONDS));
            connection.write("HTTP/1.1 200 OK\r\nContent-Length: 10\r\n\r\nhello body");
            await(() -> exchange.response() != null, "No response");

            // the subscription is already done
            Assertions.assertFalse(exchange.cancel());

            try (ByteBodyHttpResponse<?> response = (ByteBodyHttpResponse<?>) exchange.response()) {
                Assertions.assertEquals("hello body", response.byteBody().buffer().get().toString(StandardCharsets.UTF_8));
            }
            Assertions.assertFalse(connection.awaitClosed(0));
        }
    }

    @ParameterizedTest
    @EnumSource(Api.class)
    void closingAnUnconsumedResponse(Api api) throws Exception {
        try (ServerUnderTest server = server();
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class);
             RawUpstream upstream = new RawUpstream()) {
            Exchange exchange = api.start(client, HttpRequest.GET(upstream.uri("/unconsumed")), null);
            RawUpstream.Connection connection = upstream.nextConnection(TIMEOUT_SECONDS);
            Assertions.assertNotNull(connection);
            Assertions.assertTrue(connection.awaitRequest(TIMEOUT_SECONDS));
            connection.write("HTTP/1.1 200 OK\r\nContent-Length: 65536\r\n\r\n");
            connection.write(new byte[16 * 1024]);
            await(() -> exchange.response() != null, "No response");

            ((ByteBodyHttpResponse<?>) exchange.response()).close();

            if (isJdkClient(client)) {
                // the JDK client closes the connection of a response whose body was not read
                Assertions.assertTrue(connection.awaitClosed(TIMEOUT_SECONDS), "The connection was not closed");
            } else {
                // the Netty client drains the rest of the body (up to a limit) instead, and keeps
                // the connection
                Assertions.assertFalse(connection.awaitClosed(1), "The connection was closed");
                connection.write(new byte[48 * 1024]);
                Assertions.assertFalse(connection.awaitClosed(1), "The connection was closed");
            }
        }
    }

    private static boolean isJdkClient(RawHttpClient client) {
        return client.getClass().getName().contains(".jdk.");
    }

    private static void writeQuietly(RawUpstream.Connection connection, String response) {
        try {
            connection.write(response);
        } catch (IOException e) {
            // the client closed the connection
        }
    }

    private static void await(BooleanSupplier condition, String message) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                Assertions.fail(message);
            }
            Thread.sleep(10);
        }
    }

    private static ServerUnderTest server() {
        // HTTP/1.1 only: the JDK client does not send a request body while it waits for the answer
        // to its h2c upgrade. A long read timeout, so that it does not close the connection of an
        // exchange that is still running within the timeouts of the test
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME, Map.of(
            "micronaut.http.client.alpn-modes", "http/1.1",
            "micronaut.http.client.read-timeout", "60s"
        ));
    }

    /**
     * The API an exchange is sent with.
     */
    enum Api {
        PUBLISHER {
            @Override
            Exchange start(RawHttpClient client, HttpRequest<?> request, CloseableByteBody body) {
                AtomicReference<HttpResponse<?>> received = new AtomicReference<>();
                AtomicBoolean done = new AtomicBoolean();
                Disposable subscription = Mono.from(client.exchange(request, body, null))
                    .doFinally(signal -> done.set(true))
                    .subscribe(received::set, error -> { });
                return new Exchange() {
                    @Override
                    boolean cancel() {
                        boolean active = !done.get();
                        subscription.dispose();
                        return active;
                    }

                    @Override
                    HttpResponse<?> response() {
                        return received.get();
                    }
                };
            }
        };

        abstract Exchange start(RawHttpClient client, HttpRequest<?> request, CloseableByteBody body);
    }

    /**
     * A started exchange.
     */
    abstract static class Exchange {
        /**
         * @return Whether the exchange was still running
         */
        abstract boolean cancel();

        /**
         * @return The response, or {@code null} if none was delivered (yet)
         */
        abstract HttpResponse<?> response();
    }

    /**
     * A streaming request body that records what the client does with its source.
     */
    static final class TrackedBody {
        final AtomicBoolean cancelled = new AtomicBoolean();
        final AtomicBoolean completed = new AtomicBoolean();
        final AtomicBoolean discarded = new AtomicBoolean();
        final CloseableByteBody body;

        TrackedBody(Flux<byte[]> source) {
            ReadBufferFactory buffers = ReadBufferFactory.getJdkFactory();
            // the buffers are leak tracked, so they are created on a thread that the test thread
            // starts, which inherits its leak detection scope, not on a thread of the client
            ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
            executor.prestartAllCoreThreads();
            Flux<ReadBuffer> tracked = source
                .doOnCancel(() -> cancelled.set(true))
                .doOnComplete(() -> completed.set(true))
                .publishOn(Schedulers.fromExecutorService(executor))
                .doFinally(signal -> executor.shutdown())
                .map(buffers::adapt);
            body = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE)
                .adapt(tracked, BodySizeLimits.UNLIMITED, null, () -> discarded.set(true));
        }

        /**
         * @return Whether the client is done with the source: it was sent completely, or cancelled
         */
        boolean released() {
            return completed.get() || cancelled.get() && discarded.get();
        }

        @Override
        public String toString() {
            return "body[cancelled=" + cancelled + ", completed=" + completed + ", discarded=" + discarded + "]";
        }
    }
}
