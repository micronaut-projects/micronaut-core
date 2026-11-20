package io.micronaut.http.server.netty.websocket;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.netty.websocket.WebSocketSessionRepository;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import io.micronaut.websocket.annotation.ServerWebSocket;
import io.netty.channel.Channel;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Completing an asynchronous open handler does not resume reading while the frames it replayed
 * wait for a message handler that has not completed.
 */
@MicronautTest
@Property(name = "spec.name", value = "WebSocketOpenKeepsBackpressureTest")
class WebSocketOpenKeepsBackpressureTest {
    static volatile CompletableFuture<Void> open = new CompletableFuture<>();
    static final CompletableFuture<Void> message = new CompletableFuture<>();

    @Inject
    EmbeddedServer server;

    @Inject
    WebSocketSessionRepository repository;

    @AfterEach
    void complete() {
        open.complete(null);
        message.complete(null);
    }

    @Test
    void openDoesNotResumeReadingPastThePendingLimit() throws Exception {
        String upgrade = "GET /open-backpressure HTTP/1.1\r\n" +
            "Host: localhost:" + server.getPort() + "\r\n" +
            "Upgrade: websocket\r\n" +
            "Connection: Upgrade\r\n" +
            "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n" +
            "Sec-WebSocket-Version: 13\r\n" +
            "\r\n";
        ByteArrayOutputStream frames = new ByteArrayOutputStream();
        for (int i = 0; i < 70; i++) {
            frames.write(new byte[] {(byte) 0x81, (byte) 0x81, 0, 0, 0, 0, 'a'});
        }
        try (Socket socket = new Socket("127.0.0.1", server.getPort())) {
            socket.setSoTimeout(5000);
            socket.getOutputStream().write(upgrade.getBytes(StandardCharsets.ISO_8859_1));
            socket.getOutputStream().flush();
            InputStream in = socket.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            while (!out.toString(StandardCharsets.ISO_8859_1).contains("\r\n\r\n")) {
                int n = in.read(buf);
                Assertions.assertNotEquals(-1, n, "closed before the upgrade");
                out.write(buf, 0, n);
            }
            Channel found = null;
            long lookup = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (found == null && System.nanoTime() < lookup) {
                found = repository.getChannelGroup().stream()
                    .filter(c -> c.pipeline().get(NettyServerWebSocketHandler.class) != null)
                    .findFirst().orElse(null);
                if (found == null) {
                    Thread.sleep(10);
                }
            }
            Assertions.assertNotNull(found, "no WebSocket channel");
            Channel channel = found;
            NettyServerWebSocketHandler handler = channel.pipeline().get(NettyServerWebSocketHandler.class);
            socket.getOutputStream().write(frames.toByteArray());
            socket.getOutputStream().flush();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (heldWhileOpening(channel, handler) < 70 && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            Assertions.assertEquals(70, heldWhileOpening(channel, handler));
            open.complete(null);
            deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (handler.isOpening() && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            boolean autoRead = channel.eventLoop().submit(() -> channel.config().isAutoRead()).get(5, TimeUnit.SECONDS);
            Assertions.assertFalse(autoRead, "reading resumed while the replayed frames wait");
        }
    }

    private static int heldWhileOpening(Channel channel, NettyServerWebSocketHandler handler) throws Exception {
        return channel.eventLoop().submit(handler::framesHeldWhileOpening).get(5, TimeUnit.SECONDS);
    }

    @ServerWebSocket("/open-backpressure")
    @Requires(property = "spec.name", value = "WebSocketOpenKeepsBackpressureTest")
    static class Ws {
        @OnOpen
        CompletableFuture<Void> onOpen(WebSocketSession session) {
            return open;
        }

        @OnMessage
        CompletableFuture<Void> message(String m) {
            return message;
        }
    }
}
