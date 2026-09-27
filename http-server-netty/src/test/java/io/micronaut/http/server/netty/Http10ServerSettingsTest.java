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
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.context.event.HttpRequestReceivedEvent;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With {@code micronaut.server.http10-keep-alive}, a client that asks
 * to keep the connection keeps it after a response of known length; with
 * {@code micronaut.server.reject-unsupported-http-versions}, an unknown version is a {@code 505}.
 * Both are off by default.
 */
class Http10ServerSettingsTest {
    private static final String SPEC_NAME = "Http10ServerSettingsTest";

    private static ApplicationContext ctx;
    private static EmbeddedServer server;
    private static ApplicationContext optedInCtx;
    private static EmbeddedServer optedInServer;

    @BeforeAll
    static void start() {
        ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.port", -1));
        server = ctx.getBean(EmbeddedServer.class).start();
        optedInCtx = ApplicationContext.run(Map.of(
            "spec.name", SPEC_NAME,
            "micronaut.server.port", -1,
            "micronaut.server.http10-keep-alive", true,
            "micronaut.server.reject-unsupported-http-versions", true));
        optedInServer = optedInCtx.getBean(EmbeddedServer.class).start();
    }

    @AfterAll
    static void stop() {
        if (ctx != null) {
            ctx.close();
        }
        if (optedInCtx != null) {
            optedInCtx.close();
        }
    }

    @Test
    void byDefaultAnHttp10ClientThatAsksForKeepAliveIsClosedAsBefore() throws IOException {
        String response = exchange(server, "GET /http10-settings/fixed HTTP/1.0\r\nConnection: keep-alive\r\n\r\n");
        assertTrue(response.startsWith("HTTP/1.0 200"), response);
        assertFalse(response.toLowerCase().contains("keep-alive"), response);
        assertTrue(response.endsWith("fixed"), response);
    }

    @Test
    void byDefaultAnUnknownHttpVersionIsServedAsBefore() throws IOException {
        String response = exchange(server, "GET /http10-settings/fixed HTTP/9.9\r\nHost: localhost\r\nConnection: close\r\n\r\n");
        assertTrue(response.startsWith("HTTP/9.9 200"), response);
    }

    @Test
    void anHttp10ClientThatAsksForKeepAliveKeepsTheConnectionOfAResponseOfKnownLength() throws IOException {
        try (Socket socket = new Socket("127.0.0.1", optedInServer.getPort())) {
            socket.setSoTimeout(10_000);
            for (int i = 0; i < 2; i++) {
                socket.getOutputStream().write("GET /http10-settings/fixed HTTP/1.0\r\nConnection: keep-alive\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                String head = head(socket.getInputStream());
                assertTrue(head.startsWith("HTTP/1.0 200"), head);
                assertTrue(head.toLowerCase().contains("connection: keep-alive"), head);
                assertTrue(new String(socket.getInputStream().readNBytes(5), StandardCharsets.US_ASCII).equals("fixed"));
            }
        }
    }

    @Test
    void anHttp10ClientThatAsksForKeepAliveIsClosedAfterAStreamedResponse() throws IOException {
        String response = exchange(optedInServer, "GET /http10-settings/stream HTTP/1.0\r\nConnection: keep-alive\r\n\r\n");
        assertFalse(response.toLowerCase().contains("keep-alive"), response);
    }

    @Test
    void anHttp10ClientThatDoesNotAskForKeepAliveIsStillClosed() throws IOException {
        String response = exchange(optedInServer, "GET /http10-settings/fixed HTTP/1.0\r\n\r\n");
        assertTrue(response.startsWith("HTTP/1.0 200"), response);
        assertFalse(response.toLowerCase().contains("keep-alive"), response);
        assertTrue(response.endsWith("fixed"), response);
    }

    @Test
    void anHttp11RequestIsServedWhenUnsupportedVersionsAreRejected() throws IOException {
        String response = exchange(optedInServer, "GET /http10-settings/fixed HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");
        assertTrue(response.startsWith("HTTP/1.1 200"), response);
    }

    @Test
    void anUnknownHttpVersionIsNotSupported() throws IOException {
        String response = exchange(optedInServer, "GET /http10-settings/fixed HTTP/9.9\r\nHost: localhost\r\n\r\n");
        assertTrue(response.startsWith("HTTP/1.1 505"), response);
    }

    @Test
    void anUnknownHttpVersionIsAReceivedRequest() throws IOException {
        List<String> received = optedInCtx.getBean(ReceivedRequests.class).methods;
        received.clear();
        String response = exchange(optedInServer, "DELETE /http10-settings/fixed HTTP/9.9\r\nHost: localhost\r\n\r\n");
        assertTrue(response.startsWith("HTTP/1.1 505"), response);
        assertEquals(List.of("DELETE"), received);
    }

    private static String head(java.io.InputStream in) throws IOException {
        StringBuilder head = new StringBuilder();
        while (!head.toString().endsWith("\r\n\r\n")) {
            int c = in.read();
            if (c < 0) {
                break;
            }
            head.append((char) c);
        }
        return head.toString();
    }

    private static String exchange(EmbeddedServer server, String request) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", server.getPort())) {
            socket.setSoTimeout(10_000);
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class ReceivedRequests implements ApplicationEventListener<HttpRequestReceivedEvent> {
        final List<String> methods = new CopyOnWriteArrayList<>();

        @Override
        public void onApplicationEvent(HttpRequestReceivedEvent event) {
            methods.add(event.getSource().getMethodName());
        }
    }

    @Controller("/http10-settings")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Streams {
        @Get(value = "/fixed", produces = MediaType.TEXT_PLAIN)
        String fixed() {
            return "fixed";
        }

        @Get(value = "/stream", produces = MediaType.APPLICATION_OCTET_STREAM)
        Publisher<byte[]> stream() {
            return Flux.just("abc".getBytes(StandardCharsets.US_ASCII), "def".getBytes(StandardCharsets.US_ASCII));
        }
    }
}
