package io.micronaut.http.server.netty.websocket.errors;

import io.micronaut.websocket.annotation.OnClose;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.ServerWebSocket;

@ServerWebSocket("/ws/timeout/message")
public class TimeoutErrorSocket {

    boolean closed = false;


    @OnMessage
    public void onMessage(String blah) {
        System.out.println("blah = " + blah);
    }

    @OnClose
    public void onClose() {
        this.closed = true;
    }

    public boolean isClosed() {
        return closed;
    }
}

