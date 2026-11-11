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
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.client.RawRequestOptions;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * A request with {@code Expect: 100-continue} whose server never answers {@code 100 Continue}
 * sends its body after the configured time anyway, instead of waiting for the read timeout. A
 * server that answers with a final response instead never gets the body.
 */
@SuppressWarnings({
    "java:S2259", // The tests will show if it's null
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "java:S1192", // It's more readable without the constant
})
class ExpectContinueFallbackTest {
    static final String SPEC_NAME = "ExpectContinueFallbackTest";
    private static final long TIMEOUT_SECONDS = 10;
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private static String describe(CompletableFuture<?> pending) {
        if (!pending.isDone()) {
            return "pending";
        }
        try {
            return "completed with " + pending.get();
        } catch (Exception e) {
            return "failed with " + e.getCause();
        }
    }

    @Test
    void bodyIsSentWhenTheServerIgnoresTheExpectation() throws Exception {
        try (RawUpstream upstream = new RawUpstream();
             ServerUnderTest server = ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME, Map.of(
                 "micronaut.http.client.expect-continue-timeout", "200ms",
                 "micronaut.http.client.read-timeout", READ_TIMEOUT.toMillis() + "ms"));
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class)) {
            long start = System.nanoTime();
            CompletableFuture<HttpResponse<?>> pending = Mono.<HttpResponse<?>>from(
                client.exchange(expectRequest(upstream), streamedBody(), null, RawRequestOptions.proxy())).toFuture();
            RawUpstream.Connection connection = upstream.nextConnection(TIMEOUT_SECONDS);
            Assertions.assertNotNull(connection, "The client did not connect");
            Assertions.assertTrue(connection.awaitRequest(TIMEOUT_SECONDS), () -> "The request did not arrive; received: " + connection.received() + "; exchange: " + describe(pending));

            // the upstream ignores the expectation: it only answers once the body arrived
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
            while (!connection.received().contains("payload")) {
                Assertions.assertTrue(System.nanoTime() < deadline, "The body was not sent without 100 Continue, received: " + connection.received());
                Thread.sleep(20);
            }
            connection.write("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 2\r\n\r\nok");

            try (ByteBodyHttpResponse<?> response = (ByteBodyHttpResponse<?>) pending.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                Assertions.assertEquals(200, response.code());
                Assertions.assertEquals("ok", response.byteBody().buffer().get(TIMEOUT_SECONDS, TimeUnit.SECONDS).toString(StandardCharsets.UTF_8));
            }
            Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
            Assertions.assertTrue(elapsed.compareTo(READ_TIMEOUT) < 0, "The body was only sent after the read timeout: " + elapsed);
        }
    }

    @Test
    @ClientDisabledCondition.ClientDisabled(httpClient = ClientDisabledCondition.JDK) // the JDK client handles the expectation itself, and delivers a chunked response body outside the leak tracking scope of the test
    void streamedBodyIsNotSentAfterTheServerRejectedTheExpectation() throws Exception {
        heldBodyIsDroppedAfterTheFinalResponse(streamedBody());
    }

    @Test
    @ClientDisabledCondition.ClientDisabled(httpClient = ClientDisabledCondition.JDK) // the JDK client handles the expectation itself, and delivers a chunked response body outside the leak tracking scope of the test
    void availableBodyIsNotSentAfterTheServerRejectedTheExpectation() throws Exception {
        heldBodyIsDroppedAfterTheFinalResponse(availableBody());
    }

    /**
     * The server answers {@code 417 Expectation Failed} right away, and streams its body for
     * longer than the fallback timeout: the held request body is dropped when the final response
     * arrives, not sent when the timer fires.
     */
    private void heldBodyIsDroppedAfterTheFinalResponse(CloseableByteBody body) throws Exception {
        try (RawUpstream upstream = new RawUpstream();
             ServerUnderTest server = ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME, Map.of(
                 "micronaut.http.client.expect-continue-timeout", "200ms",
                 "micronaut.http.client.read-timeout", READ_TIMEOUT.toMillis() + "ms"));
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class)) {
            CompletableFuture<HttpResponse<?>> pending = Mono.<HttpResponse<?>>from(
                client.exchange(expectRequest(upstream), body, null, RawRequestOptions.proxy())).toFuture();
            RawUpstream.Connection connection = upstream.nextConnection(TIMEOUT_SECONDS);
            Assertions.assertNotNull(connection, "The client did not connect");
            Assertions.assertTrue(connection.awaitRequest(TIMEOUT_SECONDS), () -> "The request did not arrive; received: " + connection.received() + "; exchange: " + describe(pending));
            Assertions.assertFalse(connection.received().contains("payload"), () -> "The body was sent with the request head: " + connection.received());

            // the response body takes longer than the fallback timeout to arrive
            connection.write("HTTP/1.1 417 Expectation Failed\r\nContent-Type: text/plain\r\nTransfer-Encoding: chunked\r\n\r\n");
            for (String chunk : new String[] {"no", "pe"}) {
                Thread.sleep(300);
                connection.write(Integer.toHexString(chunk.length()) + "\r\n" + chunk + "\r\n");
            }
            connection.write("0\r\n\r\n");

            try (ByteBodyHttpResponse<?> response = (ByteBodyHttpResponse<?>) pending.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                Assertions.assertEquals(417, response.code());
                Assertions.assertEquals("nope", response.byteBody().buffer().get(TIMEOUT_SECONDS, TimeUnit.SECONDS).toString(StandardCharsets.UTF_8));
            }
            Assertions.assertFalse(connection.received().contains("payload"), () -> "The body was sent although the server rejected the expectation: " + connection.received());
            // the request was not sent completely, so the client closes the connection instead of reusing it
            Assertions.assertTrue(connection.awaitClosed(TIMEOUT_SECONDS), () -> "The client kept the connection open; received: " + connection.received());
            Assertions.assertFalse(connection.received().contains("payload"), () -> "The body was sent after the response: " + connection.received());
        }
    }

    private static MutableHttpRequest<?> expectRequest(RawUpstream upstream) {
        return HttpRequest.create(HttpMethod.POST, upstream.uri("/expect").toString())
            .header(HttpHeaders.EXPECT, "100-continue")
            .contentType(MediaType.TEXT_PLAIN_TYPE);
    }

    private static CloseableByteBody availableBody() {
        return ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE).adapt("payload".getBytes(StandardCharsets.UTF_8));
    }

    private static CloseableByteBody streamedBody() {
        // the buffers are leak tracked, so they are created and delivered on a thread that the
        // test thread starts, which inherits its leak detection scope, not on a thread of the client
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        executor.prestartAllCoreThreads();
        Flux<ReadBuffer> chunks = Flux.just("payload")
            .publishOn(Schedulers.fromExecutorService(executor))
            .map(text -> ReadBufferFactory.getJdkFactory().copyOf(text, StandardCharsets.UTF_8))
            .doFinally(signal -> executor.shutdown());
        return ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE).adapt(chunks);
    }
}
