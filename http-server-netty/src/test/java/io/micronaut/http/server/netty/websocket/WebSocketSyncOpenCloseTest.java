package io.micronaut.http.server.netty.websocket;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import io.micronaut.websocket.annotation.ServerWebSocket;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * No message reaches the handler of a session that a synchronous open handler closed.
 */
@MicronautTest
@Property(name = "spec.name", value = "WebSocketSyncOpenCloseTest")
class WebSocketSyncOpenCloseTest {
    static final AtomicInteger MESSAGES = new AtomicInteger();

    @Inject
    EmbeddedServer server;

    @Test
    void framesAfterTheOpenHandlerClosedAreDropped() throws Exception {
        try (Socket socket = new Socket("127.0.0.1", server.getPort())) {
            socket.setSoTimeout(5000);
            socket.getOutputStream().write(("GET /sync-open-close HTTP/1.1\r\n" +
                "Host: localhost:" + server.getPort() + "\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n" +
                "Sec-WebSocket-Version: 13\r\n" +
                "\r\n").getBytes(StandardCharsets.ISO_8859_1));
            socket.getOutputStream().flush();
            InputStream in = socket.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            while (!out.toString(StandardCharsets.ISO_8859_1).contains("\r\n\r\n")) {
                int n = in.read(buf);
                Assertions.assertNotEquals(-1, n, "closed before the upgrade");
                out.write(buf, 0, n);
            }
            try {
                for (int i = 0; i < 10; i++) {
                    socket.getOutputStream().write(new byte[] {(byte) 0x81, (byte) 0x82, 0, 0, 0, 0, 'h', 'i'});
                }
                socket.getOutputStream().flush();
                while (in.read(buf) != -1) {
                    // wait for the close
                }
            } catch (IOException ignored) {
                // closed while writing, or no close
            }
        }
        Assertions.assertEquals(0, MESSAGES.get());
    }

    @ServerWebSocket("/sync-open-close")
    @Requires(property = "spec.name", value = "WebSocketSyncOpenCloseTest")
    static class Ws {
        @OnOpen
        void onOpen(WebSocketSession session) {
            session.close();
        }

        @OnMessage
        void message(String m) {
            MESSAGES.incrementAndGet();
        }
    }
}
