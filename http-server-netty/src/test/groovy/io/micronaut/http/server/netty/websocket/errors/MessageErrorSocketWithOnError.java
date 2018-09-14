package io.micronaut.http.server.netty.websocket.errors;

import io.micronaut.websocket.CloseReason;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.OnError;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.ServerWebSocket;

@ServerWebSocket("/ws/errors/message-onerror")
public class MessageErrorSocketWithOnError {

    @OnMessage
    public void onMessage(String blah) {
        throw new RuntimeException("Bad things happened");
    }

    @OnError
    public void onError(RuntimeException error, WebSocketSession session) {
        session.close(CloseReason.UNSUPPORTED_DATA);
    }
}
