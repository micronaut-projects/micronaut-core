package io.micronaut.docs.websockets;

import io.micronaut.context.annotation.Requires;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.websocket.WebSocketBroadcaster;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.OnClose;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import io.micronaut.websocket.annotation.ServerWebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Predicate;

@Requires(property = "spec.name", value = "websockets")
@Secured(SecurityRule.IS_AUTHENTICATED)
@ServerWebSocket("/echo")
public class EchoServerWebSocket {
    protected static final Logger LOG = LoggerFactory.getLogger(EchoServerWebSocket.class);
    public static final String JOINED = "joined!";
    public static final String DISCONNECTED = "Disconnected!";

    private WebSocketBroadcaster broadcaster;

    public EchoServerWebSocket(WebSocketBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @OnOpen
    public void onOpen(WebSocketSession session) {
        broadcaster.broadcastSync(JOINED, isValid(session));
    }

    @OnMessage
    public void onMessage(String message, WebSocketSession session) {
        broadcaster.broadcastSync(message, isValid(session));
    }

    @OnClose
    public void onClose(WebSocketSession session) {
        broadcaster.broadcastSync(DISCONNECTED, isValid(session));
    }

    private Predicate<WebSocketSession> isValid(WebSocketSession session) {
        return s -> s.equals(session);
    }
}
