package io.micronaut.http.server.netty.websocket.errors;

import io.micronaut.websocket.CloseReason;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.*;

@ClientWebSocket
public abstract class ErrorsClient implements AutoCloseable {

    private Throwable lastError;
    private CloseReason lastReason;
    private WebSocketSession session;

    @OnOpen
    public void onOpen(WebSocketSession session) {
        this.session = session;
    }

    @OnMessage
    public void onMessage(String message) {
        System.out.println("message = " + message);
    }

    @OnError
    public void onError(Throwable error) {
        this.lastError = error;
    }

    @OnClose
    public void onClose(CloseReason closeReason) {
        this.lastReason = closeReason;
    }

    public abstract void send(String message);

    public Throwable getLastError() {
        return lastError;
    }

    public CloseReason getLastReason() {
        return lastReason;
    }

    public WebSocketSession getSession() {
        return session;
    }
}
