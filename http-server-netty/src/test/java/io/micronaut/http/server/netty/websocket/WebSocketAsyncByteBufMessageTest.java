package io.micronaut.http.server.netty.websocket;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.websocket.WebSocketClient;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.ClientWebSocket;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.ServerWebSocket;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * A {@link io.micronaut.core.io.buffer.ByteBuffer} message parameter stays readable until an asynchronous
 * message handler completes.
 */
@MicronautTest
@Property(name = "spec.name", value = "WebSocketAsyncByteBufMessageTest")
class WebSocketAsyncByteBufMessageTest {
    static final CompletableFuture<String> byteBuffer = new CompletableFuture<>();

    @Inject
    EmbeddedServer server;

    @Inject
    @Client("/")
    WebSocketClient client;

    @Test
    void byteBufferIsReadableInTheAsyncHandler() throws Exception {
        send("/async-bytebuffer");
        Assertions.assertEquals("hello", byteBuffer.get(10, TimeUnit.SECONDS));
    }

    private void send(String path) {
        Sender sender = Mono.from(client.connect(Sender.class, server.getURI().resolve(path))).block();
        sender.session.sendSync(ByteBuffer.wrap("hello".getBytes(StandardCharsets.UTF_8)));
    }

    private static CompletableFuture<Void> later(Runnable read) {
        return CompletableFuture.runAsync(read, CompletableFuture.delayedExecutor(300, TimeUnit.MILLISECONDS));
    }

    @ServerWebSocket("/async-bytebuffer")
    @Requires(property = "spec.name", value = "WebSocketAsyncByteBufMessageTest")
    static class ByteBufferWs {
        @OnMessage
        CompletableFuture<Void> message(io.micronaut.core.io.buffer.ByteBuffer<?> m) {
            return later(() -> {
                try {
                    byteBuffer.complete(m.toString(StandardCharsets.UTF_8));
                } catch (Throwable t) {
                    byteBuffer.completeExceptionally(t);
                }
            });
        }
    }

    @ClientWebSocket
    @Requires(property = "spec.name", value = "WebSocketAsyncByteBufMessageTest")
    abstract static class Sender implements AutoCloseable {
        WebSocketSession session;

        @io.micronaut.websocket.annotation.OnOpen
        void open(WebSocketSession session) {
            this.session = session;
        }

        @OnMessage
        void message(String m) {
        }
    }
}
