package io.micronaut.http.server.netty.websocket;

import io.micronaut.websocket.CloseReason;
import io.micronaut.websocket.WebSocketPongMessage;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.ClientWebSocket;
import io.micronaut.websocket.annotation.OnClose;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * A client that sends text messages and pings, and records the pong and the close reason it receives.
 */
@ClientWebSocket
public abstract class TextClientWebSocket implements AutoCloseable {

    private final CompletableFuture<String> pong = new CompletableFuture<>();
    private final CompletableFuture<CloseReason> closeReason = new CompletableFuture<>();
    private WebSocketSession session;

    @OnOpen
    public void onOpen(WebSocketSession session) {
        this.session = session;
    }

    @OnMessage
    public void onMessage(String message) {
    }

    @OnMessage
    public void onPong(WebSocketPongMessage message) {
        pong.complete(message.getContent().toString(StandardCharsets.UTF_8));
    }

    @OnClose
    public void onClose(CloseReason reason) {
        closeReason.complete(reason);
    }

    public CompletableFuture<String> sendText(String message) {
        return session.sendAsync(message);
    }

    public CompletableFuture<?> sendPing(String content) {
        return session.sendPingAsync(content.getBytes(StandardCharsets.UTF_8));
    }

    public void close(CloseReason reason) {
        session.close(reason);
    }

    public CompletableFuture<String> getPong() {
        return pong;
    }

    public CompletableFuture<CloseReason> getCloseReason() {
        return closeReason;
    }
}
