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

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.discovery.exceptions.NoAvailableServiceException;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.body.stream.AvailableByteArrayBody;
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.client.RawRequestOptions;
import io.micronaut.http.client.exceptions.ResponseClosedException;
import io.micronaut.http.client.exceptions.UnprocessedRequestException;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * The failures of a raw exchange say in which phase the request failed, so that a caller, e.g. a
 * gateway, knows whether the request can be sent again: an {@link UnprocessedRequestException}
 * was never sent, a {@link ResponseClosedException} was sent and says whether the response
 * headers had arrived. A load balanced exchange names the selected instance on the response.
 */
@SuppressWarnings({
    "java:S2259", // The tests will show if it's null
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "java:S1192", // It's more readable without the constant
})
class RawFailurePhaseTest {
    static final String SPEC_NAME = "RawFailurePhaseTest";
    private static final long TIMEOUT_SECONDS = 10;

    @Test
    void connectionRefusedIsUnprocessed() throws Exception {
        try (ServerUnderTest server = server();
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class)) {
            URI uri = closedPort("/refused");
            UnprocessedRequestException failure = Assertions.assertThrows(UnprocessedRequestException.class,
                () -> exchange(client, HttpRequest.GET(uri)).close());
            Assertions.assertEquals(UnprocessedRequestException.Reason.CONNECT, failure.getReason());
            Assertions.assertEquals(uri, failure.getUri().orElseThrow());
            Assertions.assertTrue(failure.getServiceInstance().isEmpty(), "An absolute URI is not load balanced");
            Assertions.assertTrue(UnprocessedRequestException.isUnprocessed(failure));
        }
    }

    @Test
    void noAvailableServiceIsUnprocessed() {
        Assertions.assertTrue(UnprocessedRequestException.isUnprocessed(new NoAvailableServiceException("orders")));
        Assertions.assertFalse(UnprocessedRequestException.isUnprocessed(new ResponseClosedException("closed")));
        Assertions.assertFalse(UnprocessedRequestException.isUnprocessed(null));
    }

    @Test
    void connectionClosedBeforeTheResponseReportsNoHeaders() throws Exception {
        try (RawUpstream upstream = new RawUpstream();
             ServerUnderTest server = server();
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class)) {
            CompletableFuture<HttpResponse<?>> response = Mono.<HttpResponse<?>>from(
                client.exchange(HttpRequest.GET(upstream.uri("/closed")), null, null, RawRequestOptions.proxy())).toFuture();
            // a client may send a GET again on a new connection when the first one closed without a response: close every attempt
            int attempts = 0;
            while (!response.isDone()) {
                RawUpstream.Connection connection = upstream.nextConnection(attempts == 0 ? TIMEOUT_SECONDS : 2);
                if (connection == null) {
                    break;
                }
                attempts++;
                Assertions.assertTrue(connection.awaitRequest(TIMEOUT_SECONDS), "The request did not arrive");
                connection.close();
            }
            Assertions.assertTrue(attempts > 0, "The client did not connect");

            ExecutionException failure = Assertions.assertThrows(ExecutionException.class, () -> response.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            ResponseClosedException closed = Assertions.assertInstanceOf(ResponseClosedException.class, failure.getCause(), () -> describe(failure.getCause()));
            Assertions.assertFalse(closed.isHeadersReceived());
            // the request was sent, so the server may have processed it
            Assertions.assertFalse(UnprocessedRequestException.isUnprocessed(closed));
        }
    }

    @Test
    void connectionClosedWhileTheRequestIsWrittenIsNotUnprocessed() throws Exception {
        try (RawUpstream upstream = new RawUpstream(true);
             ServerUnderTest server = server();
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class)) {
            // more than the socket buffers hold, so that the write is still in progress when the connection closes
            byte[] body = new byte[16 * 1024 * 1024];
            CompletableFuture<HttpResponse<?>> response = Mono.<HttpResponse<?>>from(client.exchange(
                HttpRequest.POST(upstream.uri("/half-read"), null).contentType(MediaType.APPLICATION_OCTET_STREAM),
                AvailableByteArrayBody.create(ByteArrayBufferFactory.INSTANCE, body),
                null,
                RawRequestOptions.proxy())).toFuture();
            RawUpstream.Connection connection = upstream.nextConnection(TIMEOUT_SECONDS);
            Assertions.assertNotNull(connection, "The client did not connect");
            Assertions.assertTrue(connection.awaitRequest(TIMEOUT_SECONDS), "The request did not arrive");
            // the server read the head and stops reading: the client cannot finish the body, and
            // the server closes its side of the connection
            connection.shutdownOutput();

            ExecutionException failure = Assertions.assertThrows(ExecutionException.class, () -> response.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            Throwable cause = failure.getCause();
            // the server received the head, so it may have processed the request: it must not be sent again
            Assertions.assertFalse(cause instanceof UnprocessedRequestException, () -> describe(cause));
            Assertions.assertFalse(UnprocessedRequestException.isUnprocessed(cause), () -> describe(cause));
            Assertions.assertTrue(connection.bytesReceived.get() > 0);
        }
    }

    @Test
    void connectionClosedDuringTheBodyReportsHeaders() throws Exception {
        try (RawUpstream upstream = new RawUpstream();
             ServerUnderTest server = server();
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class)) {
            CompletableFuture<HttpResponse<?>> pending = Mono.<HttpResponse<?>>from(
                client.exchange(HttpRequest.GET(upstream.uri("/truncated")), null, null, RawRequestOptions.proxy())).toFuture();
            RawUpstream.Connection connection = upstream.nextConnection(TIMEOUT_SECONDS);
            Assertions.assertNotNull(connection, "The client did not connect");
            Assertions.assertTrue(connection.awaitRequest(TIMEOUT_SECONDS), "The request did not arrive");
            connection.write("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 100\r\n\r\nhello");

            try (ByteBodyHttpResponse<?> response = (ByteBodyHttpResponse<?>) pending.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                Assertions.assertEquals(200, response.code());
                connection.close();

                CompletableFuture<?> body = response.byteBody().buffer();
                ExecutionException failure = Assertions.assertThrows(ExecutionException.class, () -> body.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
                ResponseClosedException closed = Assertions.assertInstanceOf(ResponseClosedException.class, failure.getCause(), () -> describe(failure.getCause()));
                Assertions.assertTrue(closed.isHeadersReceived());
            }
        }
    }

    /**
     * @return The failure with its causes and their first frames, for the assertion message
     */
    private static String describe(Throwable failure) {
        StringBuilder description = new StringBuilder();
        for (Throwable t = failure; t != null; t = t.getCause()) {
            description.append(t).append('\n');
            StackTraceElement[] frames = t.getStackTrace();
            for (int i = 0; i < Math.min(frames.length, 8); i++) {
                description.append("    at ").append(frames[i]).append('\n');
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return description.toString();
    }

    private static URI closedPort(String path) throws IOException {
        int port;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            port = socket.getLocalPort();
        }
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    private static ByteBodyHttpResponse<?> exchange(RawHttpClient client, HttpRequest<?> request) {
        HttpResponse<?> response = Mono.from(client.exchange(request, null, null, RawRequestOptions.proxy())).block();
        return Assertions.assertInstanceOf(ByteBodyHttpResponse.class, response);
    }

    @Controller("/raw-failure-phase")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class OkController {
        @Get(value = "/ok", produces = MediaType.TEXT_PLAIN)
        String ok() {
            return "ok";
        }
    }
}
