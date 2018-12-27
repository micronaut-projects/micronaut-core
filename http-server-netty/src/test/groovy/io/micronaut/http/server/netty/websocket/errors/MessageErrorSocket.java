package io.micronaut.http.server.netty.websocket.errors;

import io.micronaut.websocket.annotation.OnClose;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.ServerWebSocket;

@ServerWebSocket("/ws/errors/message")
public class MessageErrorSocket {

    boolean closed = false;


    @OnMessage
    public void onMessage(String blah) {
        throw new RuntimeException("Bad things happened");
    }

    @OnClose
    public void onClose() {
        this.closed = true;
    }

    public boolean isClosed() {
        return closed;
    }
}
