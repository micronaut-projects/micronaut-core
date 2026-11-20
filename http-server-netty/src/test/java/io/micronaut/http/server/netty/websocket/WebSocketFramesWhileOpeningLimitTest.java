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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

/**
 * The frames received while an asynchronous open handler runs are held up to a few times the message limit,
 * past it the session is closed with 1009.
 */
@MicronautTest
@Property(name = "spec.name", value = "WebSocketFramesWhileOpeningLimitTest")
class WebSocketFramesWhileOpeningLimitTest {
    static volatile CompletableFuture<Void> open = new CompletableFuture<>();

    @Inject
    EmbeddedServer server;

    @BeforeEach
    void newOpen() {
        open = new CompletableFuture<>();
    }

    @AfterEach
    void completeOpen() {
        open.complete(null);
    }

    @Test
    void framesPastTheLimitCloseTheSession() throws Exception {
        ByteArrayOutputStream frames = new ByteArrayOutputStream();
        // one message of four fragments, past the message limit
        for (int i = 0; i < 4; i++) {
            frames.write(maskedFrame(i == 0 ? 0x01 : 0x00, i == 3, 600));
        }
        Assertions.assertTrue(containsClose1009(exchange(frames.toByteArray())), "the held frames did not close the session");
    }

    @Test
    void messagesUnderTheLimitAreHandled() throws Exception {
        ByteArrayOutputStream frames = new ByteArrayOutputStream();
        // four messages within the limit add up to more than one message may have
        for (int i = 0; i < 4; i++) {
            frames.write(maskedTextFrame(600));
        }
        CompletableFuture.runAsync(() -> open.complete(null), CompletableFuture.delayedExecutor(500, java.util.concurrent.TimeUnit.MILLISECONDS));
        byte[] response = exchange(frames.toByteArray(), 4, 600);
        Assertions.assertFalse(containsClose1009(response), "the held messages closed the session");
        Assertions.assertEquals(4, countEchoes(response, 600));
    }

    @Test
    void manyFramesAreHandledOnceOpen() throws Exception {
        ByteArrayOutputStream frames = new ByteArrayOutputStream();
        // reading stops while they are held, and resumes once the open handler completed
        for (int i = 0; i < 2000; i++) {
            frames.write(maskedTextFrame(1));
        }
        CompletableFuture.runAsync(() -> open.complete(null), CompletableFuture.delayedExecutor(500, java.util.concurrent.TimeUnit.MILLISECONDS));
        byte[] response = exchange(frames.toByteArray(), 2000, 1);
        Assertions.assertFalse(containsClose1009(response), "the held frames closed the session");
        Assertions.assertEquals(2000, countEchoes(response, 1));
    }

    private byte[] exchange(byte[] frames) throws Exception {
        return exchange(frames, -1, 0);
    }

    private byte[] exchange(byte[] frames, int echoes, int echoLength) throws Exception {
        ByteArrayOutputStream request = new ByteArrayOutputStream();
        request.write(("GET /frames-while-opening HTTP/1.1\r\n" +
            "Host: localhost:" + server.getPort() + "\r\n" +
            "Upgrade: websocket\r\n" +
            "Connection: Upgrade\r\n" +
            "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n" +
            "Sec-WebSocket-Version: 13\r\n" +
            "\r\n").getBytes(StandardCharsets.ISO_8859_1));
        try (Socket socket = new Socket("127.0.0.1", server.getPort())) {
            socket.setSoTimeout(5000);
            socket.getOutputStream().write(request.toByteArray());
            socket.getOutputStream().flush();
            InputStream in = socket.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            // the frames follow the upgrade response, while the open handler still runs
            while (!out.toString(StandardCharsets.ISO_8859_1).contains("\r\n\r\n")) {
                int n = in.read(buf);
                Assertions.assertNotEquals(-1, n, "closed before the upgrade");
                out.write(buf, 0, n);
            }
            socket.getOutputStream().write(frames);
            socket.getOutputStream().flush();
            try {
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    if (containsClose1009(out.toByteArray()) || echoes > 0 && countEchoes(out.toByteArray(), echoLength) >= echoes) {
                        break;
                    }
                }
            } catch (SocketTimeoutException ignored) {
                // no close frame
            }
            byte[] response = out.toByteArray();
            Assertions.assertTrue(new String(response, StandardCharsets.ISO_8859_1).startsWith("HTTP/1.1 101"), "not upgraded");
            return response;
        }
    }

    private static int countEchoes(byte[] bytes, int length) {
        int count = 0;
        for (int i = 0; i + 1 < bytes.length; i++) {
            boolean header = length < 126
                ? (bytes[i] & 0xFF) == 0x81 && (bytes[i + 1] & 0xFF) == length
                : i + 3 < bytes.length && (bytes[i] & 0xFF) == 0x81 && (bytes[i + 1] & 0xFF) == 126 && (bytes[i + 2] & 0xFF) == (length >> 8) && (bytes[i + 3] & 0xFF) == (length & 0xFF);
            if (header) {
                count++;
            }
        }
        return count;
    }

    private static boolean containsClose1009(byte[] bytes) {
        for (int i = 0; i + 1 < bytes.length; i++) {
            if ((bytes[i] & 0xFF) == 0x88 && (bytes[i + 2] & 0xFF) == 0x03 && (bytes[i + 3] & 0xFF) == 0xF1) {
                return true;
            }
        }
        return false;
    }

    private static byte[] maskedTextFrame(int length) {
        return maskedFrame(0x01, true, length);
    }

    private static byte[] maskedFrame(int opcode, boolean fin, int length) {
        if (length < 126) {
            byte[] frame = new byte[2 + 4 + length];
            frame[0] = (byte) ((fin ? 0x80 : 0) | opcode);
            frame[1] = (byte) (0x80 | length);
            Arrays.fill(frame, 6, frame.length, (byte) 'a');
            return frame;
        }
        byte[] frame = new byte[4 + 4 + length];
        frame[0] = (byte) ((fin ? 0x80 : 0) | opcode);
        frame[1] = (byte) (0x80 | 126);
        frame[2] = (byte) (length >> 8);
        frame[3] = (byte) length;
        // a zero mask leaves the payload as it is
        Arrays.fill(frame, 8, frame.length, (byte) 'a');
        return frame;
    }

    @ServerWebSocket("/frames-while-opening")
    @Requires(property = "spec.name", value = "WebSocketFramesWhileOpeningLimitTest")
    static class Ws {
        @OnOpen
        CompletableFuture<Void> onOpen(WebSocketSession session) {
            return open;
        }

        @OnMessage(maxPayloadLength = 1024)
        void message(String m, WebSocketSession session) {
            session.sendSync(m);
        }
    }
}
