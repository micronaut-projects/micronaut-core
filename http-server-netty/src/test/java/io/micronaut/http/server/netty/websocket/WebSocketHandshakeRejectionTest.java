package io.micronaut.http.server.netty.websocket;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.ServerWebSocket;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

/**
 * An upgrade request the Netty handshaker rejects is answered.
 */
@MicronautTest
@Property(name = "spec.name", value = "WebSocketHandshakeRejectionTest")
class WebSocketHandshakeRejectionTest {
    @Inject
    EmbeddedServer server;

    @Test
    void upgradeHeaderWithExtraTokenIsAnswered() throws Exception {
        assertAnswered("GET /handshake-rejection HTTP/1.1\r\n" +
            "Host: localhost\r\n" +
            "Upgrade: websocket, foo\r\n" +
            "Connection: Upgrade\r\n" +
            "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n" +
            "Sec-WebSocket-Version: 13\r\n" +
            "\r\n");
    }

    @Test
    void missingKeyIsAnswered() throws Exception {
        assertAnswered("GET /handshake-rejection HTTP/1.1\r\n" +
            "Host: localhost\r\n" +
            "Upgrade: websocket\r\n" +
            "Connection: Upgrade\r\n" +
            "Sec-WebSocket-Version: 13\r\n" +
            "\r\n");
    }

    private void assertAnswered(String request) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", server.getPort())) {
            socket.setSoTimeout(2500);
            socket.getOutputStream().write(request.getBytes(StandardCharsets.ISO_8859_1));
            socket.getOutputStream().flush();
            InputStream in = socket.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            boolean closed = false;
            try {
                int n = in.read(new byte[1024]);
                closed = n == -1;
                if (n > 0) {
                    out.write(1);
                }
            } catch (SocketTimeoutException ignored) {
                // silent
            }
            Assertions.assertTrue(out.size() > 0 || closed, "no response and the connection is still open");
        }
    }

    @ServerWebSocket("/handshake-rejection")
    @Requires(property = "spec.name", value = "WebSocketHandshakeRejectionTest")
    static class Ws {
        @OnMessage
        String message(String m) {
            return m;
        }
    }
}
