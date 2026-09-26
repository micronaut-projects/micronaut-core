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
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.client.RawRequestOptions;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A request with {@code Expect: 100-continue} holds its body until the server answers: a
 * {@code 100 Continue} releases it, a final response drops it, and without an answer it is sent
 * after the configured timeout. Other interim responses are skipped.
 */
class ExpectContinueTest {
    private static final long TIMEOUT_SECONDS = 10;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void continueReleasesTheBody(boolean streamed) throws Exception {
        try (Upstream upstream = new Upstream();
             ApplicationContext ctx = ApplicationContext.run(Map.of("micronaut.http.client.expect-continue-timeout", "10s"));
             RawHttpClient client = ctx.createBean(RawHttpClient.class)) {
            CompletableFuture<HttpResponse<?>> pending = exchange(client, upstream, streamed);
            upstream.awaitReceived("\r\n\r\n");
            Assertions.assertFalse(upstream.received().contains("payload"), "The body was sent with the request head");

            upstream.write("HTTP/1.1 100 Continue\r\n\r\n");
            upstream.awaitReceived("payload");
            upstream.write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok");

            assertResponse(pending, 200, "ok");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void finalResponseDropsTheBody(boolean streamed) throws Exception {
        try (Upstream upstream = new Upstream();
             ApplicationContext ctx = ApplicationContext.run(Map.of("micronaut.http.client.expect-continue-timeout", "10s"));
             RawHttpClient client = ctx.createBean(RawHttpClient.class)) {
            CompletableFuture<HttpResponse<?>> pending = exchange(client, upstream, streamed);
            upstream.awaitReceived("\r\n\r\n");

            upstream.write("HTTP/1.1 417 Expectation Failed\r\nContent-Length: 4\r\n\r\nnope");

            assertResponse(pending, 417, "nope");
            // the request was not sent completely, so the connection is not reused
            Assertions.assertTrue(upstream.awaitClosed(), "The client kept the connection open");
            Assertions.assertFalse(upstream.received().contains("payload"), "The body was sent although the server rejected the expectation");
        }
    }

    @Test
    void bodyIsSentAfterTheTimeoutWithoutAnAnswer() throws Exception {
        try (Upstream upstream = new Upstream();
             ApplicationContext ctx = ApplicationContext.run(Map.of("micronaut.http.client.expect-continue-timeout", "200ms"));
             RawHttpClient client = ctx.createBean(RawHttpClient.class)) {
            Assertions.assertEquals(Duration.ofMillis(200), ctx.getBean(HttpClientConfiguration.class).getExpectContinueTimeout().orElseThrow());
            CompletableFuture<HttpResponse<?>> pending = exchange(client, upstream, false);

            upstream.awaitReceived("payload");
            upstream.write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok");

            assertResponse(pending, 200, "ok");
        }
    }

    @Test
    void interimResponsesBeforeTheFinalResponseAreSkipped() throws Exception {
        try (Upstream upstream = new Upstream();
             ApplicationContext ctx = ApplicationContext.run();
             RawHttpClient client = ctx.createBean(RawHttpClient.class)) {
            CompletableFuture<HttpResponse<?>> pending = Mono.<HttpResponse<?>>from(
                client.exchange(HttpRequest.GET(upstream.uri()), null, null, RawRequestOptions.proxy())).toFuture();
            upstream.awaitReceived("\r\n\r\n");

            upstream.write("HTTP/1.1 103 Early Hints\r\nLink: </style.css>; rel=preload\r\n\r\n"
                + "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok");

            assertResponse(pending, 200, "ok");
        }
    }

    private static CompletableFuture<HttpResponse<?>> exchange(RawHttpClient client, Upstream upstream, boolean streamed) {
        ByteBodyFactory factory = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);
        CloseableByteBody body = streamed
            ? factory.adapt(Flux.just("payload").map(text -> (ReadBuffer) ReadBufferFactory.getJdkFactory().copyOf(text, StandardCharsets.UTF_8)))
            : factory.adapt("payload".getBytes(StandardCharsets.UTF_8));
        return Mono.<HttpResponse<?>>from(client.exchange(
            HttpRequest.create(HttpMethod.POST, upstream.uri())
                .header(HttpHeaders.EXPECT, "100-continue")
                .contentType(MediaType.TEXT_PLAIN_TYPE),
            body, null, RawRequestOptions.proxy())).toFuture();
    }

    private static void assertResponse(CompletableFuture<HttpResponse<?>> pending, int code, String body) throws Exception {
        try (ByteBodyHttpResponse<?> response = (ByteBodyHttpResponse<?>) pending.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            Assertions.assertEquals(code, response.code());
            Assertions.assertEquals(body, response.byteBody().buffer().get(TIMEOUT_SECONDS, TimeUnit.SECONDS).toString(StandardCharsets.UTF_8));
        }
    }

    /**
     * Accepts one connection and records what the client sends on it.
     */
    private static final class Upstream implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final StringBuffer received = new StringBuffer();
        private final CountDownLatch accepted = new CountDownLatch(1);
        private final CountDownLatch closed = new CountDownLatch(1);
        private volatile Socket socket;

        Upstream() throws IOException {
            serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            Thread thread = new Thread(this::read, "expect-continue-upstream");
            thread.setDaemon(true);
            thread.start();
        }

        String uri() {
            return "http://127.0.0.1:" + serverSocket.getLocalPort() + "/expect";
        }

        String received() {
            return received.toString();
        }

        private void read() {
            try (Socket s = serverSocket.accept()) {
                socket = s;
                accepted.countDown();
                InputStream in = s.getInputStream();
                byte[] buffer = new byte[1024];
                int n;
                while ((n = in.read(buffer)) >= 0) {
                    received.append(new String(buffer, 0, n, StandardCharsets.ISO_8859_1));
                }
            } catch (IOException ignored) {
                // closed by the test
            } finally {
                closed.countDown();
            }
        }

        void awaitReceived(String text) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
            while (!received().contains(text)) {
                Assertions.assertTrue(System.nanoTime() < deadline, () -> "Did not receive " + text.strip() + ", received: " + received());
                Thread.sleep(10);
            }
        }

        boolean awaitClosed() throws InterruptedException {
            return closed.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        void write(String text) throws Exception {
            Assertions.assertTrue(accepted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "The client did not connect");
            socket.getOutputStream().write(text.getBytes(StandardCharsets.ISO_8859_1));
            socket.getOutputStream().flush();
        }

        @Override
        public void close() throws IOException {
            serverSocket.close();
            Socket s = socket;
            if (s != null) {
                s.close();
            }
        }
    }
}
