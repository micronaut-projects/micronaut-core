package io.micronaut.http.server.netty.websocket;

import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.ClientWebSocket;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import io.reactivex.Single;

import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;

@ClientWebSocket("/chat/{topic}/{username}")
public abstract class ChatClientWebSocket implements AutoCloseable {

    private WebSocketSession session;
    private String topic;
    private String username;
    private Collection<String> replies = new ConcurrentLinkedQueue<>();

    @OnOpen
    public void onOpen(String topic, String username, WebSocketSession session) {
        this.topic = topic;
        this.username = username;
        this.session = session;
    }

    public String getTopic() {
        return topic;
    }

    public String getUsername() {
        return username;
    }

    public Collection<String> getReplies() {
        return replies;
    }

    public WebSocketSession getSession() {
        return session;
    }

    @OnMessage
    public void onMessage(
            String message) {
        replies.add(message);
    }

    public abstract void send(String message);

    public abstract Future<String> sendAsync(String message);

    public abstract Single<String> sendRx(String message);

}
