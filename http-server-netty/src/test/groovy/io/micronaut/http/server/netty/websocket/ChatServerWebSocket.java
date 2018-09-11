package io.micronaut.http.server.netty.websocket;

import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.OnClose;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import io.micronaut.websocket.annotation.ServerWebSocket;

import java.util.Set;

@ServerWebSocket("/chat/{topic}/{username}")
public class ChatServerWebSocket {

    @OnOpen
    public void onOpen(String topic, String username, WebSocketSession session) {
        Set<? extends WebSocketSession> openSessions = session.getOpenSessions();
        for (WebSocketSession openSession : openSessions) {
            if(isValid(topic, session, openSession)) {
                openSession.sendSync("[" + username + "] Joined!" );
            }
        }
    }

    @OnMessage
    public void onMessage(
            String topic,
            String username,
            String message,
            WebSocketSession session) {

        Set<? extends WebSocketSession> openSessions = session.getOpenSessions();
        for (WebSocketSession openSession : openSessions) {
            if(isValid(topic, session, openSession)) {
                openSession.sendSync("[" + username + "] " + message );
            }
        }
    }

    @OnClose
    public void onClose(
            String topic,
            String username,
            WebSocketSession session) {
        Set<? extends WebSocketSession> openSessions = session.getOpenSessions();
        for (WebSocketSession openSession : openSessions) {
            if(isValid(topic, session, openSession)) {
                openSession.sendSync("[" + username + "] Disconnected!" );
            }
        }
    }

    private boolean isValid(String topic, WebSocketSession session, WebSocketSession openSession) {
        return openSession != session && topic.equalsIgnoreCase(openSession.getUriVariables().get("topic", String.class).orElse(null));
    }
}
