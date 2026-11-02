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
package io.micronaut.http.server.netty.websocket;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.ServerWebSocket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/**
 * An upgrade request for a WebSocket version other than 13, 8 or 7 (including the Hixie drafts,
 * which send no version) is refused with a response, and the connection is closed after it.
 */
class WebSocketUnsupportedVersionTest {
    private static final String SPEC_NAME = "WebSocketUnsupportedVersionTest";

    private EmbeddedServer server;

    @BeforeEach
    void start() {
        server = ApplicationContext.run(EmbeddedServer.class, Collections.singletonMap("spec.name", SPEC_NAME));
    }

    @AfterEach
    void stop() {
        server.close();
    }

    @Test
    void hixie76IsRefused() throws Exception {
        assertRefused("GET /unsupported-version HTTP/1.1\r\n" +
            "Host: localhost:" + server.getPort() + "\r\n" +
            "Upgrade: websocket\r\n" +
            "Connection: Upgrade\r\n" +
            "Sec-WebSocket-Key1: 1 2\r\n" +
            "Sec-WebSocket-Key2: 3 4\r\n" +
            "Origin: http://localhost:" + server.getPort() + "\r\n" +
            "\r\n");
    }

    @Test
    void hixie76KeysWithoutDigitsAreRefused() throws Exception {
        assertRefused("GET /unsupported-version HTTP/1.1\r\n" +
            "Host: localhost:" + server.getPort() + "\r\n" +
            "Upgrade: websocket\r\n" +
            "Connection: Upgrade\r\n" +
            "Sec-WebSocket-Key1: a b\r\n" +
            "Sec-WebSocket-Key2: cd\r\n" +
            "Origin: http://localhost:" + server.getPort() + "\r\n" +
            "\r\n");
    }

    @Test
    void hixie75IsRefused() throws Exception {
        assertRefused("GET /unsupported-version HTTP/1.1\r\n" +
            "Host: localhost:" + server.getPort() + "\r\n" +
            "Upgrade: websocket\r\n" +
            "Connection: Upgrade\r\n" +
            "Origin: http://localhost:" + server.getPort() + "\r\n" +
            "\r\n");
    }

    @Test
    void unknownVersionIsRefused() throws Exception {
        assertRefused("GET /unsupported-version HTTP/1.1\r\n" +
            "Host: localhost:" + server.getPort() + "\r\n" +
            "Upgrade: websocket\r\n" +
            "Connection: Upgrade\r\n" +
            "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n" +
            "Sec-WebSocket-Version: 99\r\n" +
            "\r\n");
    }

    @Test
    void handshakeFailureIsAnsweredWithBadRequest() throws Exception {
        // a supported version without a key fails the handshake before anything was written
        String response = assertRefused("GET /unsupported-version HTTP/1.1\r\n" +
            "Host: localhost:" + server.getPort() + "\r\n" +
            "Upgrade: websocket\r\n" +
            "Connection: Upgrade\r\n" +
            "Sec-WebSocket-Version: 13\r\n" +
            "\r\n");
        Assertions.assertTrue(response.startsWith("HTTP/1.1 400 "), response);
    }

    private String assertRefused(String request) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", server.getPort())) {
            socket.setSoTimeout(5000);
            socket.getOutputStream().write(request.getBytes(StandardCharsets.ISO_8859_1));
            socket.getOutputStream().flush();
            InputStream in = socket.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            boolean closed = false;
            try {
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
                closed = true;
            } catch (SocketTimeoutException ignored) {
                // still open
            }
            String response = new String(out.toByteArray(), StandardCharsets.ISO_8859_1);
            Assertions.assertTrue(response.startsWith("HTTP/1.1 4"), "not refused: " + response);
            Assertions.assertFalse(response.contains("101 Switching Protocols"), response);
            Assertions.assertTrue(closed, "the connection stays open after the refusal: " + response);
            return response;
        }
    }

    @ServerWebSocket("/unsupported-version")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Ws {
        @OnMessage
        String message(String m) {
            return m;
        }
    }

    @Controller
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Plain {
        @Get("/unsupported-version-plain")
        String plain() {
            return "plain";
        }
    }
}
