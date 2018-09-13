package io.micronaut.http.server.netty.websocket;

// tag::imports[]
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.*;
import io.reactivex.Single;
import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;
// end::imports[]
import java.util.concurrent.Future;

// tag::class[]
@ClientWebSocket("/chat/{topic}/{username}") // <1>
public abstract class ChatClientWebSocket implements AutoCloseable { // <2>

    private WebSocketSession session;
    private String topic;
    private String username;
    private Collection<String> replies = new ConcurrentLinkedQueue<>();

    @OnOpen
    public void onOpen(String topic, String username, WebSocketSession session) { // <3>
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
        replies.add(message); // <4>
    }

// end::class[]
    public abstract void send(String message);

    public abstract Future<String> sendAsync(String message);

    public abstract Single<String> sendRx(String message);

}
