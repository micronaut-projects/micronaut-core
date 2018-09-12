package io.micronaut.http.server.netty.websocket;

import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.ClientWebSocket;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import io.netty.buffer.ByteBuf;
import io.reactivex.Single;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;

@ClientWebSocket("/binary/chat/{topic}/{username}")
public abstract class BinaryChatClientWebSocket implements AutoCloseable{

    private WebSocketSession session;
    private String topic;
    private String username;
    private Collection<String> replies = new ConcurrentLinkedQueue<>();

    @OnOpen
    public void onOpen(String topic, String username, WebSocketSession session) {
        this.topic = topic;
        this.username = username;
        this.session = session;
        System.out.println("Client session opened for username = " + username);
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
            byte[] message) {
        System.out.println("Client received message = " + new String(message));
        replies.add(new String(message));
    }

    public abstract void send(byte[] message);

    public abstract Future<ByteBuf> sendAsync(ByteBuf message);

    public abstract Single<ByteBuffer> sendRx(ByteBuffer message);
}
