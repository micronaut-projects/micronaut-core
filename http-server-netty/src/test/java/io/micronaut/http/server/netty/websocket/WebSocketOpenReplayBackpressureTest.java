package io.micronaut.http.server.netty.websocket;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
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
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * When the frames held during an asynchronous open pause reading as they are handled, the end of
 * the open must not resume reading.
 */
@MicronautTest
@Property(name = "spec.name", value = "WebSocketOpenReplayBackpressureTest")
class WebSocketOpenReplayBackpressureTest {
    static volatile CompletableFuture<Void> open = new CompletableFuture<>();
    static volatile CompletableFuture<Channel> channel = new CompletableFuture<>();
    static final Sinks.Empty<String> PENDING = Sinks.empty();

    @Inject
    EmbeddedServer server;

    @AfterEach
    void completeOpen() {
        open.complete(null);
        // let the held messages complete, so that their frames are released
        PENDING.tryEmitEmpty();
    }

    @Test
    void theOpenDoesNotResumeReadingPausedByTheReplay() throws Exception {
        try (Socket socket = new Socket("127.0.0.1", server.getPort())) {
            socket.setSoTimeout(5000);
            socket.getOutputStream().write(("GET /open-replay-backpressure HTTP/1.1\r\n" +
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
            ByteArrayOutputStream frames = new ByteArrayOutputStream();
            for (int i = 0; i < 100; i++) {
                frames.write(new byte[] {(byte) 0x81, (byte) 0x82, 0, 0, 0, 0, 'h', 'i'});
            }
            socket.getOutputStream().write(frames.toByteArray());
            socket.getOutputStream().flush();
            Channel ch = channel.get(5, TimeUnit.SECONDS);
            // let the frames arrive while the open handler runs
            Thread.sleep(500);
            CompletableFuture<Boolean> autoRead = new CompletableFuture<>();
            ch.eventLoop().execute(() -> {
                open.complete(null);
                ch.eventLoop().execute(() -> autoRead.complete(ch.config().isAutoRead()));
            });
            Assertions.assertFalse(autoRead.get(5, TimeUnit.SECONDS), "reading resumed while the replayed frames are pending");
        }
    }

    @ServerWebSocket("/open-replay-backpressure")
    @Requires(property = "spec.name", value = "WebSocketOpenReplayBackpressureTest")
    static class Ws {
        @OnOpen
        CompletableFuture<Void> onOpen(WebSocketSession session) throws Exception {
            Class<?> type = session.getClass();
            while (!type.getSimpleName().equals("NettyWebSocketSession")) {
                type = type.getSuperclass();
            }
            Field field = type.getDeclaredField("channel");
            field.setAccessible(true);
            channel.complete((Channel) field.get(session));
            return open;
        }

        @OnMessage
        Mono<String> message(String m) {
            return PENDING.asMono();
        }
    }
}
