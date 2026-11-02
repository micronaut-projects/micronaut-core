package io.micronaut.http.server.netty.websocket;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import io.micronaut.websocket.annotation.ServerWebSocket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * No message reaches the handler of a session that a synchronous open handler closed.
 */
class WebSocketSyncOpenCloseTest {
    static final AtomicInteger MESSAGES = new AtomicInteger();
    static final AtomicInteger OPENED_AFTER_FRAMES = new AtomicInteger();
    static final AtomicReference<CountDownLatch> FRAMES_SENT = new AtomicReference<>(new CountDownLatch(0));

    private EmbeddedServer server;

    @BeforeEach
    void startServer() {
        server = ApplicationContext.run(EmbeddedServer.class, Collections.singletonMap("spec.name", "WebSocketSyncOpenCloseTest"));
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

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
            while (!new String(out.toByteArray(), StandardCharsets.ISO_8859_1).contains("\r\n\r\n")) {
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

    /**
     * The open handler closes the session only once the client has sent its frames after the 101 response, so the
     * frames are read while the open handler runs rather than after the session it closed is gone.
     */
    @Test
    void framesSentWhileTheOpenHandlerRunsAreDropped() throws Exception {
        FRAMES_SENT.set(new CountDownLatch(1));
        try (Socket socket = new Socket("127.0.0.1", server.getPort())) {
            socket.setSoTimeout(5000);
            socket.getOutputStream().write(("GET /sync-open-close-after-frames HTTP/1.1\r\n" +
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
            while (!new String(out.toByteArray(), StandardCharsets.ISO_8859_1).contains("\r\n\r\n")) {
                int n = in.read(buf);
                Assertions.assertNotEquals(-1, n, "closed before the upgrade");
                out.write(buf, 0, n);
            }
            Assertions.assertTrue(new String(out.toByteArray(), StandardCharsets.ISO_8859_1).startsWith("HTTP/1.1 101 "), "not upgraded");
            Assertions.assertTrue(OPENED_AFTER_FRAMES.get() == 0, "the open handler completed before the frames were sent");
            try {
                for (int i = 0; i < 10; i++) {
                    socket.getOutputStream().write(new byte[] {(byte) 0x81, (byte) 0x82, 0, 0, 0, 0, 'h', 'i'});
                }
                socket.getOutputStream().flush();
                FRAMES_SENT.get().countDown();
                while (in.read(buf) != -1) {
                    // wait for the close
                }
            } catch (IOException ignored) {
                // closed while writing, or no close
            }
        }
        Assertions.assertEquals(1, OPENED_AFTER_FRAMES.get(), "the open handler did not wait for the frames");
        Assertions.assertEquals(0, MESSAGES.get());
    }

    @ServerWebSocket("/sync-open-close-after-frames")
    @Requires(property = "spec.name", value = "WebSocketSyncOpenCloseTest")
    static class WsAfterFrames {
        @OnOpen
        void onOpen(WebSocketSession session) throws InterruptedException {
            if (FRAMES_SENT.get().await(5, TimeUnit.SECONDS)) {
                OPENED_AFTER_FRAMES.incrementAndGet();
                // let the frames reach the server before the session is closed
                Thread.sleep(200);
            }
            session.close();
        }

        @OnMessage
        void message(String m) {
            MESSAGES.incrementAndGet();
        }
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
