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
import org.junit.jupiter.api.BeforeEach;
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
 * A burst of valid messages sent while an asynchronous open handler runs is handled, while
 * zero-length frames cannot be held without a bound.
 */
@MicronautTest
@Property(name = "spec.name", value = "WebSocketFramesWhileOpeningBudgetTest")
class WebSocketFramesWhileOpeningBudgetTest {
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
    void aBurstOfValidMessagesIsHandled() throws Exception {
        ByteArrayOutputStream frames = new ByteArrayOutputStream();
        for (int i = 0; i < 4; i++) {
            frames.write(maskedTextFrame(600));
        }
        byte[] response = exchange(frames.toByteArray(), 4 * (4 + 600));
        Assertions.assertFalse(containsClose1009(response), "a burst of valid messages closed the session");
        int echoed = 0;
        String text = new String(response, StandardCharsets.ISO_8859_1);
        for (int i = text.indexOf("aaaa"); i != -1; i = text.indexOf("a".repeat(600), i + 600)) {
            echoed++;
        }
        Assertions.assertEquals(4, echoed, "not every message was handled: " + response.length + " " + text.substring(0, Math.min(text.length(), 400)));
    }

    @Test
    void zeroLengthFramesAreBounded() throws Exception {
        ByteArrayOutputStream frames = new ByteArrayOutputStream();
        for (int i = 0; i < 1000; i++) {
            frames.write(new byte[] {(byte) 0x81, (byte) 0x80, 0, 0, 0, 0});
        }
        byte[] response = exchange(frames.toByteArray(), -1);
        Assertions.assertTrue(containsClose1009(response), "the held empty frames did not close the session");
    }

    private byte[] exchange(byte[] frames, int expectedEchoBytes) throws Exception {
        ByteArrayOutputStream request = new ByteArrayOutputStream();
        request.write(("GET /frames-while-opening-budget HTTP/1.1\r\n" +
            "Host: localhost:" + server.getPort() + "\r\n" +
            "Upgrade: websocket\r\n" +
            "Connection: Upgrade\r\n" +
            "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n" +
            "Sec-WebSocket-Version: 13\r\n" +
            "\r\n").getBytes(StandardCharsets.ISO_8859_1));
        try (Socket socket = new Socket("127.0.0.1", server.getPort())) {
            socket.setSoTimeout(3000);
            socket.getOutputStream().write(request.toByteArray());
            socket.getOutputStream().flush();
            InputStream in = socket.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            while (!out.toString(StandardCharsets.ISO_8859_1).contains("\r\n\r\n")) {
                int n = in.read(buf);
                Assertions.assertNotEquals(-1, n, "closed before the upgrade");
                out.write(buf, 0, n);
            }
            int headerEnd = out.toString(StandardCharsets.ISO_8859_1).indexOf("\r\n\r\n") + 4;
            // the frames follow the upgrade response, while the open handler still runs
            socket.getOutputStream().write(frames);
            socket.getOutputStream().flush();
            Thread.sleep(200);
            open.complete(null);
            try {
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    if (containsClose1009(out.toByteArray())
                        || (expectedEchoBytes > 0 && out.size() - headerEnd >= expectedEchoBytes)) {
                        break;
                    }
                }
            } catch (SocketTimeoutException ignored) {
                // nothing more
            }
            byte[] response = out.toByteArray();
            Assertions.assertTrue(new String(response, StandardCharsets.ISO_8859_1).startsWith("HTTP/1.1 101"), "not upgraded");
            return response;
        }
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

    @ServerWebSocket("/frames-while-opening-budget")
    @Requires(property = "spec.name", value = "WebSocketFramesWhileOpeningBudgetTest")
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
