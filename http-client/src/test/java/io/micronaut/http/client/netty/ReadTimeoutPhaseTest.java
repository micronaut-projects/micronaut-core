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
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.client.RawRequestOptions;
import io.micronaut.http.client.exceptions.ReadTimeoutException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * A read timeout says whether it elapsed while the response was awaited, or while its body was
 * read: only the first may be retried.
 */
class ReadTimeoutPhaseTest {
    private static final long TIMEOUT_SECONDS = 10;
    private static final String PARTIAL_RESPONSE = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 100\r\n\r\nhello";

    @Test
    void rawTimeoutBeforeTheHeaders() throws Exception {
        try (Upstream upstream = new Upstream();
             ApplicationContext ctx = context();
             RawHttpClient client = ctx.createBean(RawHttpClient.class)) {
            CompletableFuture<HttpResponse<?>> pending = rawExchange(client, upstream.uri());
            upstream.awaitRequest();

            Assertions.assertFalse(assertTimeout(pending).isHeadersReceived());
        }
    }

    @Test
    void rawTimeoutWhileTheBodyIsRead() throws Exception {
        try (Upstream upstream = new Upstream();
             ApplicationContext ctx = context();
             RawHttpClient client = ctx.createBean(RawHttpClient.class)) {
            CompletableFuture<HttpResponse<?>> pending = rawExchange(client, upstream.uri());
            upstream.awaitRequest();
            upstream.write(PARTIAL_RESPONSE);

            try (ByteBodyHttpResponse<?> response = (ByteBodyHttpResponse<?>) pending.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                Assertions.assertEquals(200, response.code());
                Assertions.assertTrue(assertTimeout(response.byteBody().buffer()).isHeadersReceived());
            }
        }
    }

    @Test
    void bufferingClientTimeoutBeforeTheHeaders() throws Exception {
        try (Upstream upstream = new Upstream();
             ApplicationContext ctx = context();
             HttpClient client = ctx.createBean(HttpClient.class, upstream.uri().toURL())) {
            CompletableFuture<HttpResponse<String>> pending = Mono.from(client.exchange(HttpRequest.GET("/"), String.class)).toFuture();
            upstream.awaitRequest();

            Assertions.assertFalse(assertTimeout(pending).isHeadersReceived());
        }
    }

    @Test
    void bufferingClientTimeoutWhileTheBodyIsRead() throws Exception {
        try (Upstream upstream = new Upstream();
             ApplicationContext ctx = context();
             HttpClient client = ctx.createBean(HttpClient.class, upstream.uri().toURL())) {
            CompletableFuture<HttpResponse<String>> pending = Mono.from(client.exchange(HttpRequest.GET("/"), String.class)).toFuture();
            upstream.awaitRequest();
            upstream.write(PARTIAL_RESPONSE);

            Assertions.assertTrue(assertTimeout(pending).isHeadersReceived());
        }
    }

    @Test
    void theTwoTimeoutsTellThePhase() {
        Assertions.assertFalse(ReadTimeoutException.TIMEOUT_EXCEPTION.isHeadersReceived());
        Assertions.assertTrue(ReadTimeoutException.BODY_TIMEOUT_EXCEPTION.isHeadersReceived());
    }

    private static ApplicationContext context() {
        return ApplicationContext.run(Map.of("micronaut.http.client.read-timeout", "500ms"));
    }

    private static CompletableFuture<HttpResponse<?>> rawExchange(RawHttpClient client, URI uri) {
        return Mono.<HttpResponse<?>>from(client.exchange(HttpRequest.GET(uri), null, null, RawRequestOptions.proxy())).toFuture();
    }

    private static ReadTimeoutException assertTimeout(CompletableFuture<?> pending) {
        ExecutionException failure = Assertions.assertThrows(ExecutionException.class, () -> pending.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        return Assertions.assertInstanceOf(ReadTimeoutException.class, failure.getCause(), () -> String.valueOf(failure.getCause()));
    }

    /**
     * Accepts one connection, reads the request head and answers only what the test writes.
     */
    private static final class Upstream implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final CountDownLatch requested = new CountDownLatch(1);
        private volatile Socket socket;

        Upstream() throws IOException {
            serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            Thread thread = new Thread(this::serve, "read-timeout-upstream");
            thread.setDaemon(true);
            thread.start();
        }

        URI uri() {
            return URI.create("http://127.0.0.1:" + serverSocket.getLocalPort() + "/");
        }

        private void serve() {
            try {
                Socket s = serverSocket.accept();
                socket = s;
                InputStream in = s.getInputStream();
                StringBuilder head = new StringBuilder();
                int b;
                while ((b = in.read()) != -1) {
                    head.append((char) b);
                    if (head.toString().endsWith("\r\n\r\n")) {
                        requested.countDown();
                        break;
                    }
                }
            } catch (IOException ignored) {
                // closed by the test
            }
        }

        void awaitRequest() throws InterruptedException {
            Assertions.assertTrue(requested.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "The request did not arrive");
        }

        void write(String text) throws IOException {
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
