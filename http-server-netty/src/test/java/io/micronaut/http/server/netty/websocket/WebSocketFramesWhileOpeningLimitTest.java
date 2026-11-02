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
import java.util.zip.Deflater;

/**
 * The frames received while an asynchronous open handler runs are held up to a few times the
 * message limit, past it the session is closed with 1009.
 */
@MicronautTest
@Property(name = "spec.name", value = "WebSocketFramesWhileOpeningLimitTest")
class WebSocketFramesWhileOpeningLimitTest {
    static volatile CompletableFuture<Void> open = new CompletableFuture<>();

    @Inject
    EmbeddedServer server;

    @AfterEach
    void completeOpen() {
        open.complete(null);
    }

    @Test
    void framesPastTheLimitCloseTheSession() throws Exception {
        ByteArrayOutputStream request = new ByteArrayOutputStream();
        request.write(("GET /frames-while-opening HTTP/1.1\r\n" +
            "Host: localhost:" + server.getPort() + "\r\n" +
            "Upgrade: websocket\r\n" +
            "Connection: Upgrade\r\n" +
            "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n" +
            "Sec-WebSocket-Version: 13\r\n" +
            "Sec-WebSocket-Extensions: permessage-deflate\r\n" +
            "\r\n").getBytes(StandardCharsets.ISO_8859_1));
        ByteArrayOutputStream frames = new ByteArrayOutputStream();
        // compressed, so that the held frames inflate past the limit within one read
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        for (int i = 0; i < 8; i++) {
            frames.write(maskedCompressedTextFrame(deflater, 900));
        }
        deflater.end();
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
            socket.getOutputStream().write(frames.toByteArray());
            socket.getOutputStream().flush();
            try {
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    if (containsClose1009(out.toByteArray())) {
                        break;
                    }
                }
            } catch (SocketTimeoutException ignored) {
                // no close frame
            }
            byte[] response = out.toByteArray();
            Assertions.assertTrue(new String(response, StandardCharsets.ISO_8859_1).startsWith("HTTP/1.1 101"), "not upgraded");
            Assertions.assertTrue(containsClose1009(response), "the held frames did not close the session: " + new String(response, StandardCharsets.ISO_8859_1));
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

    private static byte[] maskedCompressedTextFrame(Deflater deflater, int length) {
        byte[] text = new byte[length];
        Arrays.fill(text, (byte) 'a');
        deflater.setInput(text);
        byte[] buf = new byte[1024];
        int n = deflater.deflate(buf, 0, buf.length, Deflater.SYNC_FLUSH);
        // permessage-deflate drops the trailing 00 00 FF FF of the flush
        int payload = n - 4;
        byte[] frame = new byte[2 + 4 + payload];
        // FIN, RSV1 (compressed), text
        frame[0] = (byte) 0xC1;
        frame[1] = (byte) (0x80 | payload);
        // a zero mask leaves the payload as it is
        System.arraycopy(buf, 0, frame, 6, payload);
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
        String message(String m) {
            return m;
        }
    }
}
