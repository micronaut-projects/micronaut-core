package io.micronaut.http.server.netty.websocket.errors;

import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.ServerWebSocket;

@ServerWebSocket("/ws/errors/message")
public class MessageErrorSocket {

    @OnMessage
    public void onMessage(String blah) {
        throw new RuntimeException("Bad things happened");
    }
}
