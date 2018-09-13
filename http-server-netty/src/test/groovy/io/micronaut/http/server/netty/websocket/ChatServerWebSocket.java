package io.micronaut.http.server.netty.websocket;

import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.*;
import java.util.function.Predicate;

@ServerWebSocket("/chat/{topic}/{username}") // <1>
public class ChatServerWebSocket {

    @OnOpen // <2>
    public void onOpen(String topic, String username, WebSocketSession session) {
        String msg = "[" + username + "] Joined!";
        session.broadcastSync(msg, isValid(topic, session));
    }

    @OnMessage // <3>
    public void onMessage(
            String topic,
            String username,
            String message,
            WebSocketSession session) {
        String msg = "[" + username + "] " + message;
        session.broadcastSync(msg, isValid(topic, session)); // <4>
    }

    @OnClose // <5>
    public void onClose(
            String topic,
            String username,
            WebSocketSession session) {
        String msg = "[" + username + "] Disconnected!";
        session.broadcastSync(msg, isValid(topic, session));
    }

    private Predicate<WebSocketSession> isValid(String topic, WebSocketSession session) {
        return s -> s != session && topic.equalsIgnoreCase(s.getUriVariables().get("topic", String.class, null));
    }
}
