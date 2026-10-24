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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.body.AsyncRequestBody;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.ServerWebSocket;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every request that runs the filters has an {@link AsyncRequestBody}: a WebSocket upgrade, and
 * the requests the server answers with an error before routing, a malformed URI and an
 * unsupported HTTP version, keep their response when a filter looks at the body.
 */
class AsyncRequestBodyOfEarlyRequestsTest {
    private static final String SPEC_NAME = "AsyncRequestBodyOfEarlyRequestsTest";
    private static final String CHECKED = "X-Body-Checked";

    private static ApplicationContext ctx;
    private static EmbeddedServer server;

    @BeforeAll
    static void start() {
        ctx = ApplicationContext.run(Map.of(
            "spec.name", SPEC_NAME,
            "micronaut.server.port", -1,
            "micronaut.server.reject-unsupported-http-versions", true));
        server = ctx.getBean(EmbeddedServer.class).start();
    }

    @AfterAll
    static void stop() {
        if (ctx != null) {
            ctx.close();
        }
    }

    @Test
    void aMalformedUriIsABadRequest() throws IOException {
        String head = exchange("GET /early/%zz HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");
        assertTrue(head.startsWith("HTTP/1.1 400 "), head);
    }

    @Test
    void anUnsupportedHttpVersionIsRejected() throws IOException {
        String head = exchange("GET /early HTTP/9.9\r\nHost: localhost\r\nConnection: close\r\n\r\n");
        assertTrue(head.startsWith("HTTP/1.1 505 "), head);
    }

    @Test
    void aWebSocketUpgradeSwitchesProtocols() throws IOException {
        String head = exchange("GET /early-ws HTTP/1.1\r\n"
            + "Host: localhost\r\n"
            + "Connection: Upgrade\r\n"
            + "Upgrade: websocket\r\n"
            + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
            + "Sec-WebSocket-Version: 13\r\n"
            + "\r\n");
        assertTrue(head.startsWith("HTTP/1.1 101 "), head);
        assertTrue(head.toLowerCase(Locale.ROOT).contains(CHECKED.toLowerCase(Locale.ROOT)), head);
    }

    private static String exchange(String request) throws IOException {
        try (Socket socket = new Socket(server.getHost(), server.getPort())) {
            socket.setSoTimeout(30_000);
            OutputStream out = socket.getOutputStream();
            out.write(request.getBytes(StandardCharsets.US_ASCII));
            out.flush();
            return readHead(socket.getInputStream());
        }
    }

    private static String readHead(InputStream in) throws IOException {
        StringBuilder head = new StringBuilder();
        int matched = 0;
        while (matched < 4) {
            int b = in.read();
            if (b < 0) {
                break;
            }
            head.append((char) b);
            matched = (b == '\r' && (matched == 0 || matched == 2)) || (b == '\n' && (matched == 1 || matched == 3)) ? matched + 1 : 0;
        }
        return head.toString();
    }

    @ServerFilter("/**")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class BodyFilter {
        @RequestFilter
        void look(HttpRequest<?> request) {
            // throws if the request has no body source
            request.setAttribute(CHECKED, AsyncRequestBody.of(request).hasBody());
        }

        @ResponseFilter
        void mark(HttpRequest<?> request, MutableHttpResponse<?> response) {
            request.getAttribute(CHECKED).ifPresent(checked -> response.header(CHECKED, checked.toString()));
        }
    }

    @ServerWebSocket("/early-ws")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class EarlySocket {
        @OnMessage
        String echo(String message) {
            return message;
        }
    }
}
