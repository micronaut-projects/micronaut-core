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
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A streamed body of unknown length is not chunked for an HTTP/1.0 client, which does not know
 * the chunked coding: the end of the connection ends it.
 */
class Http10StreamedResponseTest {
    private static final String SPEC_NAME = "Http10StreamedResponseTest";

    private static ApplicationContext ctx;
    private static EmbeddedServer server;

    @BeforeAll
    static void start() {
        ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.port", -1));
        server = ctx.getBean(EmbeddedServer.class).start();
    }

    @AfterAll
    static void stop() {
        if (ctx != null) {
            ctx.close();
        }
    }

    @Test
    void aStreamedBodyToAnHttp10ClientIsFramedByTheEndOfTheConnection() throws IOException {
        String response = exchange("GET /http10/stream HTTP/1.0\r\n\r\n");
        assertTrue(response.startsWith("HTTP/1.0 200"), response);
        assertFalse(response.toLowerCase().contains("transfer-encoding"), response);
        assertTrue(response.endsWith("\r\n\r\nabcdef"), response);
    }

    @Test
    void aStreamedBodyToAnHttp10KeepAliveClientClosesTheConnection() throws IOException {
        String response = exchange("GET /http10/stream HTTP/1.0\r\nConnection: keep-alive\r\n\r\n");
        assertTrue(response.startsWith("HTTP/1.0 200"), response);
        String lower = response.toLowerCase();
        assertFalse(lower.contains("transfer-encoding"), response);
        assertFalse(lower.contains("content-length"), response);
        assertFalse(lower.contains("keep-alive"), response);
        assertTrue(response.endsWith("\r\n\r\nabcdef"), response);
    }

    @Test
    void aHeadRequestToAStreamedBodyFromAnHttp10Client() throws IOException {
        String response = exchange("HEAD /http10/stream HTTP/1.0\r\n\r\n");
        assertTrue(response.startsWith("HTTP/1.0 200"), response);
        assertFalse(response.toLowerCase().contains("transfer-encoding"), response);
        assertTrue(response.endsWith("\r\n\r\n"), response);
    }

    @Test
    void aNoContentResponseToAnHttp10Client() throws IOException {
        String response = exchange("GET /http10/no-content HTTP/1.0\r\n\r\n");
        assertTrue(response.startsWith("HTTP/1.0 204"), response);
        assertFalse(response.toLowerCase().contains("transfer-encoding"), response);
        assertFalse(response.toLowerCase().contains("content-length"), response);
        assertTrue(response.endsWith("\r\n\r\n"), response);
    }

    @Test
    void aNotModifiedResponseToAnHttp10Client() throws IOException {
        String response = exchange("GET /http10/not-modified HTTP/1.0\r\n\r\n");
        assertTrue(response.startsWith("HTTP/1.0 304"), response);
        assertFalse(response.toLowerCase().contains("transfer-encoding"), response);
        assertTrue(response.endsWith("\r\n\r\n"), response);
    }

    @Test
    void aStreamedBodyToAnHttp11ClientIsStillChunked() throws IOException {
        String response = exchange("GET /http10/stream HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");
        assertTrue(response.toLowerCase().contains("transfer-encoding: chunked"), response);
    }

    private static String exchange(String request) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", server.getPort())) {
            socket.setSoTimeout(10_000);
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }

    @Controller("/http10")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Streams {
        @Get(value = "/stream", produces = MediaType.APPLICATION_OCTET_STREAM)
        Publisher<byte[]> stream() {
            return Flux.just("abc".getBytes(StandardCharsets.US_ASCII), "def".getBytes(StandardCharsets.US_ASCII));
        }

        @Get(value = "/no-content", produces = MediaType.APPLICATION_OCTET_STREAM)
        HttpResponse<Publisher<byte[]>> noContent() {
            return HttpResponse.<Publisher<byte[]>>status(HttpStatus.NO_CONTENT).body(Flux.empty());
        }

        @Get(value = "/not-modified", produces = MediaType.APPLICATION_OCTET_STREAM)
        HttpResponse<Publisher<byte[]>> notModified() {
            return HttpResponse.<Publisher<byte[]>>status(HttpStatus.NOT_MODIFIED).body(Flux.empty());
        }
    }
}
