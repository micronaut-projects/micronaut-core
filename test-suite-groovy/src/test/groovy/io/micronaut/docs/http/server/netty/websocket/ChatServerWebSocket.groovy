
package io.micronaut.docs.http.server.netty.websocket

//tag::clazz[]

import io.micronaut.websocket.WebSocketBroadcaster
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.OnClose
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.micronaut.websocket.annotation.ServerWebSocket

import java.util.function.Predicate

@ServerWebSocket("/chat/{topic}/{username}") // <1>
class ChatServerWebSocket {
    private WebSocketBroadcaster broadcaster

    ChatServerWebSocket(WebSocketBroadcaster broadcaster) {
        this.broadcaster = broadcaster
    }

    @OnOpen // <2>
    void onOpen(String topic, String username, WebSocketSession session) {
        String msg = "[" + username + "] Joined!"
        broadcaster.broadcastSync(msg, isValid(topic, session))
    }

    @OnMessage // <3>
     void onMessage(
            String topic,
            String username,
            String message,
            WebSocketSession session) {
        String msg = "[" + username + "] " + message
        broadcaster.broadcastSync(msg, isValid(topic, session)) // <4>
    }

    @OnClose // <5>
     void onClose(
            String topic,
            String username,
            WebSocketSession session) {
        String msg = "[" + username + "] Disconnected!"
        broadcaster.broadcastSync(msg, isValid(topic, session))
    }

    private Predicate<WebSocketSession> isValid(String topic, WebSocketSession session) {
        return { s -> s != session && topic.equalsIgnoreCase(s.getUriVariables().get("topic", String.class, null)) }
    }
}
//end::clazz[]
