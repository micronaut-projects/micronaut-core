package io.micronaut.http.server.netty.websocket;

import io.micronaut.websocket.annotation.ClientWebSocket;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import io.reactivex.Single;

import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;

@ClientWebSocket("/pojo/chat/{topic}/{username}")
public abstract class PojoChatClientWebSocket implements AutoCloseable {

    private String topic;
    private String username;
    private Collection<Message> replies = new ConcurrentLinkedQueue<>();

    @OnOpen
    public void onOpen(String topic, String username) {
        this.topic = topic;
        this.username = username;
    }

    public String getTopic() {
        return topic;
    }

    public String getUsername() {
        return username;
    }

    public Collection<Message> getReplies() {
        return replies;
    }

    @OnMessage
    public void onMessage(
            Message message) {
        System.out.println("Client received message = " + message);
        replies.add(message);
    }

    public abstract void send(Message message);

    public abstract Future<Message> sendAsync(Message message);

    public abstract Single<Message> sendRx(Message message);
}
