package io.micronaut.http.server.netty.websocket;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.websocket.WebSocketBroadcaster;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.OnClose;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import io.micronaut.websocket.annotation.ServerWebSocket;
import java.util.function.Predicate;

@ServerWebSocket("/context/chat/{topic}/{username}")
public class ContextAwareChatServerWebSocket {
    private WebSocketBroadcaster broadcaster;
    private String subProtocol;

    public ContextAwareChatServerWebSocket(WebSocketBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @OnOpen
    public void onOpen(String topic, String username, WebSocketSession session) {
        this.subProtocol = session.getSubprotocol().orElse(null);
        String msg = "[" + username + "] Joined! Request URI: " + getRequestUri();
        broadcaster.broadcastSync(msg, isValid(topic, session));
    }

    @OnMessage
    public void onMessage(
            String topic,
            String username,
            String message,
            WebSocketSession session) {
        String msg = "[" + username + "] " + message + " Request URI: " + getRequestUri();
        broadcaster.broadcastSync(msg, isValid(topic, session));
    }

    @OnClose
    public void onClose(
            String topic,
            String username,
            WebSocketSession session) {
        String msg = "[" + username + "] Disconnected! Request URI: " + getRequestUri();
        broadcaster.broadcastSync(msg, isValid(topic, session));
    }

    private Predicate<WebSocketSession> isValid(String topic, WebSocketSession session) {
        return s -> s != session && topic.equalsIgnoreCase(s.getUriVariables().get("topic", String.class, null));
    }

    private String getRequestUri() {
        HttpRequest<Object> currentRequest = ServerRequestContext.currentRequest().orElse(null);
        if (currentRequest == null) {
            return "<no-context>";
        }
        return currentRequest.getUri().toString();
    }

    public String getSubProtocol() {
        return subProtocol;
    }
}
