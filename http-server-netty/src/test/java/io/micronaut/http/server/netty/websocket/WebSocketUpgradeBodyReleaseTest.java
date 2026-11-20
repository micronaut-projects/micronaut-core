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
 * WebSocket upgrade requests that carry a body must release it (404 route miss, successful 101 with the
 * body in the same packet). Checked by the leak presence detector (executionError) of the tests.
 */
class WebSocketUpgradeBodyReleaseTest {
    private static final String SPEC_NAME = "WebSocketUpgradeBodyReleaseTest";

    @Test
    void upgradeRequestBodiesAreReleased() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.port", -1))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            for (int i = 0; i < 4; i++) {
                String miss = send(server, "/body-release-missing", 16 * 1024);
                assertTrue(miss.startsWith("HTTP/1.1 404"), miss);
                String ok = send(server, "/body-release-ws", 1);
                assertTrue(ok.startsWith("HTTP/1.1 101"), ok);
            }
            Thread.sleep(500);
        }
    }

    private static String send(EmbeddedServer server, String path, int bodyLength) throws Exception {
        try (Socket socket = new Socket(server.getHost(), server.getPort())) {
            socket.setSoTimeout(10_000);
            OutputStream out = socket.getOutputStream();
            ByteArrayOutputStream req = new ByteArrayOutputStream();
            req.write(("GET " + path + " HTTP/1.1\r\nHost: localhost\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\nContent-Length: " + bodyLength + "\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII));
            req.write(new byte[bodyLength]);
            out.write(req.toByteArray());
            out.flush();
            return readHead(socket.getInputStream());
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

    @ServerWebSocket("/body-release-ws")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class EchoSocket {
        @OnMessage
        String onMessage(String message) {
            return message;
        }
    }
}
