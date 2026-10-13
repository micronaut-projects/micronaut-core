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
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.stream.AvailableByteArrayBody;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A failure while the response is handed to the connection, e.g. an {@link Error} of a buffer
 * allocation, after the response was encoded: the client gets an error response, or a closed
 * connection, instead of waiting for a response that never comes.
 */
class ResponseWriteFailureTest {

    private static ApplicationContext context;
    private static EmbeddedServer server;

    @BeforeAll
    static void start() {
        context = ApplicationContext.run(Map.of("spec.name", "ResponseWriteFailureTest"));
        server = context.getBean(EmbeddedServer.class).start();
    }

    @AfterAll
    static void stop() {
        context.close();
    }

    @Test
    void aFailureWhileWritingTheResponseAnswersWithAnError() throws IOException {
        String response = get("/write-failure/broken", true);
        assertTrue(response.startsWith("HTTP/1.1 500 "), response);
        assertTrue(context.getBean(WriteFailureController.class).released.get(), "the body is released");
    }

    @Test
    void aKeepAliveConnectionIsClosedAfterTheErrorResponse() throws IOException {
        // the response is read until the server closes the connection
        String response = get("/write-failure/broken", false);
        assertTrue(response.startsWith("HTTP/1.1 500 "), response);
        assertTrue(response.toLowerCase(Locale.ROOT).contains("connection: close"), response);
    }

    @Test
    void theRequestsBeforeTheFailureAreAnsweredAndTheServerStaysUsable() throws IOException {
        // two requests on one keep-alive connection: the first is answered, the second fails
        // and closes the connection
        String responses = send("GET /write-failure/fine HTTP/1.1\r\nHost: x\r\n\r\n"
            + "GET /write-failure/broken HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(responses.startsWith("HTTP/1.1 200 "), responses);
        int second = responses.indexOf("HTTP/1.1 ", 1);
        assertTrue(second > 0, responses);
        assertTrue(responses.substring(second).startsWith("HTTP/1.1 500 "), responses);
        assertTrue(responses.substring(second).toLowerCase(Locale.ROOT).contains("connection: close"), responses);
        // a request after the failure, on a new connection
        assertTrue(get("/write-failure/fine", true).startsWith("HTTP/1.1 200 "));
    }

    @Test
    void anHttp10RequestGetsTheErrorResponse() throws IOException {
        String response = send("GET /write-failure/broken HTTP/1.0\r\n\r\n");
        assertTrue(response.startsWith("HTTP/1.0 500 "), response);
    }

    @Test
    void aResponseWrittenOnTheEventLoopGetsTheErrorResponse() throws IOException {
        // the reactive route completes on the event loop, where the write is not handed over
        String response = get("/write-failure/reactive", true);
        assertTrue(response.startsWith("HTTP/1.1 500 "), response);
    }

    @Test
    void anHttp2StreamGetsTheErrorResponse() {
        try (ApplicationContext h2 = ApplicationContext.run(Map.of(
            "spec.name", "ResponseWriteFailureTest",
            "micronaut.server.http-version", "2.0",
            "micronaut.server.ssl.enabled", false,
            "micronaut.http.client.plaintext-mode", "h2c_prior_knowledge",
            "micronaut.http.client.read-timeout", "10s"))) {
            EmbeddedServer h2Server = h2.getBean(EmbeddedServer.class).start();
            try (HttpClient client = h2.createBean(HttpClient.class, h2Server.getURL())) {
                HttpClientResponseException e = assertThrows(HttpClientResponseException.class,
                    () -> client.toBlocking().exchange(HttpRequest.GET("/write-failure/broken"), byte[].class));
                assertEquals(500, e.getStatus().getCode());
                // the connection is still used for the other streams
                assertEquals("fine", client.toBlocking().retrieve("/write-failure/fine"));
            }
        }
    }

    private static String get(String path, boolean close) throws IOException {
        return send("GET " + path + " HTTP/1.1\r\n"
            + "Host: " + server.getHost() + "\r\n"
            + (close ? "Connection: close\r\n" : "")
            + "\r\n");
    }

    /**
     * Send the requests, and read the responses until the server closes the connection.
     */
    private static String send(String requests) throws IOException {
        try (Socket socket = new Socket(server.getHost(), server.getPort())) {
            socket.setSoTimeout(10_000);
            OutputStream out = socket.getOutputStream();
            out.write(requests.getBytes(StandardCharsets.US_ASCII));
            out.flush();
            InputStream in = socket.getInputStream();
            try {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (SocketTimeoutException e) {
                return fail("No response, and the connection stayed open", e);
            }
        }
    }

    @Controller("/write-failure")
    @Requires(property = "spec.name", value = "ResponseWriteFailureTest")
    static class WriteFailureController {
        final AtomicBoolean released = new AtomicBoolean();

        @Get(value = "/broken", produces = MediaType.APPLICATION_OCTET_STREAM)
        HttpResponse<ByteBody> broken() {
            return HttpResponse.ok(AvailableByteArrayBody.create(new FailingReadBuffer(released)));
        }

        @Get(value = "/reactive", produces = MediaType.APPLICATION_OCTET_STREAM)
        Mono<HttpResponse<ByteBody>> reactive() {
            return Mono.fromSupplier(() -> HttpResponse.ok(AvailableByteArrayBody.create(new FailingReadBuffer(released))));
        }

        @Get(value = "/fine", produces = MediaType.TEXT_PLAIN)
        String fine() {
            return "fine";
        }
    }

    /**
     * A buffer whose bytes cannot be read, like a buffer whose copy fails to allocate.
     */
    private static final class FailingReadBuffer extends ReadBuffer {
        private final AtomicBoolean released;

        FailingReadBuffer(AtomicBoolean released) {
            this.released = released;
        }

        @Override
        public int readable() {
            return 4;
        }

        @Override
        public ReadBuffer duplicate() {
            return this;
        }

        @Override
        public ReadBuffer split(int splitPosition) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ReadBuffer move() {
            return this;
        }

        @Override
        public void toArray(byte[] destination, int offset) {
            throw new OutOfMemoryError("Simulated failure to copy the response body");
        }

        @Override
        public void close() {
            released.set(true);
        }

        @Override
        protected boolean isConsumed() {
            return false;
        }

        @Override
        protected byte[] peekArray(int n) {
            throw new OutOfMemoryError("Simulated failure to copy the response body");
        }
    }
}
