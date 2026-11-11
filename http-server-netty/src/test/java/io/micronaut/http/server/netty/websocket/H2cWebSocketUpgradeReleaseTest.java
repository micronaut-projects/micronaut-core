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
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.ServerWebSocket;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A WebSocket upgrade over HTTP/1.1 to a server that also accepts h2c upgrades. The h2c upgrade
 * handler aggregates every request with an {@code Upgrade} header into a full request, whose
 * content becomes the body of the WebSocket upgrade request: it is released. Checked by the leak
 * presence detector of the tests.
 */
class H2cWebSocketUpgradeReleaseTest {
    private static final String SPEC_NAME = "H2cWebSocketUpgradeReleaseTest";

    @Test
    void theBodyOfTheUpgradeRequestIsReleased() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "spec.name", SPEC_NAME,
            "micronaut.server.http-version", "2.0",
            "micronaut.server.ssl.enabled", false))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class);
            server.start();
            try (Socket socket = new Socket(server.getHost(), server.getPort())) {
                socket.setSoTimeout(10_000);
                OutputStream out = socket.getOutputStream();
                out.write(("GET /h2c-ws HTTP/1.1\r\nHost: localhost\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                    + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                out.flush();
                String head = readHead(socket.getInputStream());
                assertTrue(head.startsWith("HTTP/1.1 101"), head);
            }
        }
    }

    private static String readHead(InputStream in) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int matched = 0;
        while (matched < 4) {
            int b = in.read();
            if (b < 0) {
                break;
            }
            bytes.write(b);
            matched = (b == '\r' && matched % 2 == 0) || (b == '\n' && matched % 2 == 1) ? matched + 1 : 0;
        }
        return bytes.toString(StandardCharsets.US_ASCII);
    }

    @ServerWebSocket("/h2c-ws")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class EchoSocket {
        @OnMessage
        String onMessage(String message) {
            return message;
        }
    }
}
