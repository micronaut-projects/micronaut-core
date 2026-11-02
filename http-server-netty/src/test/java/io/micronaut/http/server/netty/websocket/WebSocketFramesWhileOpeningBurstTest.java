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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

/**
 * A burst of messages sent right after the upgrade, while an asynchronous open handler runs, is
 * not closed as one oversized message, but the number of held frames is bounded.
 */
@MicronautTest
@Property(name = "spec.name", value = "WebSocketFramesWhileOpeningBurstTest")
class WebSocketFramesWhileOpeningBurstTest {
    static volatile CompletableFuture<Void> open = new CompletableFuture<>();

    @Inject
    EmbeddedServer server;

    @AfterEach
    void completeOpen() {
        open.complete(null);
    }

    @Test
    void aBurstOfMessagesWithinTheLimitIsHandled() throws Exception {
        open = new CompletableFuture<>();
        ByteArrayOutputStream frames = new ByteArrayOutputStream();
        for (int i = 0; i < 4; i++) {
            frames.write(maskedTextFrame(600));
        }
        byte[] response = exchange(frames.toByteArray(), 4);
        Assertions.assertFalse(containsClose1009(response), "the burst closed the session");
        Assertions.assertEquals(4, countEchoes(response), new String(response, StandardCharsets.ISO_8859_1));
    }

    @Test
    void tooManyHeldFramesCloseTheSession() throws Exception {
        open = new CompletableFuture<>();
        ByteArrayOutputStream frames = new ByteArrayOutputStream();
        for (int i = 0; i < 5000; i++) {
            frames.write(new byte[] {(byte) 0x81, (byte) 0x80, 0, 0, 0, 0});
        }
        byte[] response = exchange(frames.toByteArray(), 0);
        Assertions.assertTrue(containsClose1009(response), "the held frames did not close the session");
    }

    private byte[] exchange(byte[] frames, int expectedEchoes) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", server.getPort())) {
            socket.setSoTimeout(3000);
            socket.getOutputStream().write(("GET /frames-while-opening-burst HTTP/1.1\r\n" +
                "Host: localhost:" + server.getPort() + "\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n" +
                "Sec-WebSocket-Version: 13\r\n" +
                "\r\n").getBytes(StandardCharsets.ISO_8859_1));
            socket.getOutputStream().flush();
            InputStream in = socket.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            while (!out.toString(StandardCharsets.ISO_8859_1).contains("\r\n\r\n")) {
                int n = in.read(buf);
                Assertions.assertNotEquals(-1, n, "closed before the upgrade");
                out.write(buf, 0, n);
            }
            Assertions.assertTrue(out.toString(StandardCharsets.ISO_8859_1).startsWith("HTTP/1.1 101"), "not upgraded");
            socket.getOutputStream().write(frames);
            socket.getOutputStream().flush();
            // let the frames arrive while the open handler runs
            Thread.sleep(300);
            open.complete(null);
            try {
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    byte[] bytes = out.toByteArray();
                    if (containsClose1009(bytes) || (expectedEchoes > 0 && countEchoes(bytes) >= expectedEchoes)) {
                        break;
                    }
                }
            } catch (SocketTimeoutException ignored) {
                // nothing more
            }
            return out.toByteArray();
        }
    }

    private static int countEchoes(byte[] bytes) {
        int count = 0;
        for (int i = 0; i + 3 < bytes.length; i++) {
            if ((bytes[i] & 0xFF) == 0x81 && (bytes[i + 1] & 0xFF) == 126) {
                count++;
            }
        }
        return count;
    }

    private static boolean containsClose1009(byte[] bytes) {
        for (int i = 0; i + 3 < bytes.length; i++) {
            if ((bytes[i] & 0xFF) == 0x88 && (bytes[i + 2] & 0xFF) == 0x03 && (bytes[i + 3] & 0xFF) == 0xF1) {
                return true;
            }
        }
        return false;
    }

    private static byte[] maskedTextFrame(int length) {
        byte[] frame = new byte[4 + 4 + length];
        frame[0] = (byte) 0x81;
        frame[1] = (byte) (0x80 | 126);
        frame[2] = (byte) (length >> 8);
        frame[3] = (byte) length;
        // a zero mask leaves the payload as it is
        Arrays.fill(frame, 8, frame.length, (byte) 'a');
        return frame;
    }

    @ServerWebSocket("/frames-while-opening-burst")
    @Requires(property = "spec.name", value = "WebSocketFramesWhileOpeningBurstTest")
    static class Ws {
        @OnOpen
        CompletableFuture<Void> onOpen(WebSocketSession session) {
            return open;
        }

        @OnMessage(maxPayloadLength = 1024)
        void message(String m, WebSocketSession session) {
            session.sendAsync(m);
        }
    }
}
