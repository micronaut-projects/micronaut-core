package io.micronaut.http.server.netty.websocket;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import io.micronaut.websocket.annotation.ServerWebSocket;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A WebSocket upgrade whose head failed to decode is rejected like any other request instead of upgraded.
 */
@MicronautTest
@Property(name = "spec.name", value = "WebSocketUpgradeDecoderFailureTest")
class WebSocketUpgradeDecoderFailureTest {
    static final AtomicInteger OPENED = new AtomicInteger();

    @Inject
    EmbeddedServer server;

    @Test
    void duplicateContentLengthUpgradeIsRejected() throws Exception {
        String response = send("GET /upgrade-decoder-failure HTTP/1.1\r\n" +
            "Host: localhost\r\n" +
            "Upgrade: websocket\r\n" +
            "Connection: Upgrade\r\n" +
            "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n" +
            "Sec-WebSocket-Version: 13\r\n" +
            "Content-Length: 0\r\n" +
            "Content-Length: 7\r\n" +
            "\r\n");
        Assertions.assertFalse(response.startsWith("HTTP/1.1 101"), "upgraded a request that failed to decode: " + response);
        Assertions.assertEquals(0, OPENED.get());
    }

    @Test
    void invalidHeaderNameUpgradeIsRejected() throws Exception {
        String response = send("GET /upgrade-decoder-failure HTTP/1.1\r\n" +
            "Host: localhost\r\n" +
            "Upgrade: websocket\r\n" +
            "Connection: Upgrade\r\n" +
            "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n" +
            "Sec-WebSocket-Version: 13\r\n" +
            "Bad(Name: x\r\n" +
            "X-Marker: proxy-set\r\n" +
            "\r\n");
        Assertions.assertFalse(response.startsWith("HTTP/1.1 101"), "upgraded a request that failed to decode: " + response);
        Assertions.assertTrue(response.startsWith("HTTP/1.1 400"), response);
    }

    private String send(String request) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", server.getPort())) {
            socket.setSoTimeout(2500);
            socket.getOutputStream().write(request.getBytes(StandardCharsets.ISO_8859_1));
            socket.getOutputStream().flush();
            InputStream in = socket.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            try {
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    if (out.toString(StandardCharsets.ISO_8859_1).contains("\r\n\r\n")) {
                        break;
                    }
                }
            } catch (SocketTimeoutException ignored) {
                // no (complete) response
            }
            return out.toString(StandardCharsets.ISO_8859_1);
        }
    }

    @ServerWebSocket("/upgrade-decoder-failure")
    @Requires(property = "spec.name", value = "WebSocketUpgradeDecoderFailureTest")
    static class Ws {
        @OnOpen
        void open() {
            OPENED.incrementAndGet();
        }

        @OnMessage
        String message(String m) {
            return m;
        }
    }
}
