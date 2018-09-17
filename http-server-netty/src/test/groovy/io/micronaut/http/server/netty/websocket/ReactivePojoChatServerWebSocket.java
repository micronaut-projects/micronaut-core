package io.micronaut.http.server.netty.websocket;

import io.micronaut.websocket.WebSocketBroadcaster;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.OnClose;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import io.micronaut.websocket.annotation.ServerWebSocket;
import org.reactivestreams.Publisher;

import java.util.function.Predicate;

@ServerWebSocket("/pojo/chat/{topic}/{username}")
public class ReactivePojoChatServerWebSocket {

    private WebSocketBroadcaster broadcaster;

    public ReactivePojoChatServerWebSocket(WebSocketBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @OnOpen
    public Publisher<Message> onOpen(String topic, String username, WebSocketSession session) {
        String text = "[" + username + "] Joined!";
        Message message = new Message(text);
        return broadcaster.broadcast(message, isValid(topic, session));
    }

    // tag::onmessage[]
    @OnMessage
    public Publisher<Message> onMessage(
            String topic,
            String username,
            Message message,
            WebSocketSession session) {

        String text = "[" + username + "] " + message.getText();
        Message newMessage = new Message(text);
        return broadcaster.broadcast(newMessage, isValid(topic, session));
    }
    // end::onmessage[]

    @OnClose
    public Publisher<Message> onClose(
            String topic,
            String username,
            WebSocketSession session) {

        String text = "[" + username + "] Disconnected!";
        Message message = new Message(text);
        return broadcaster.broadcast(message, isValid(topic, session));
    }

    private Predicate<WebSocketSession> isValid(String topic, WebSocketSession session) {
        return s -> s != session && topic.equalsIgnoreCase(s.getUriVariables().get("topic", String.class, null));
    }
}
