package io.micronaut.http.server.netty.websocket;

import io.micronaut.http.HttpRequest;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.ClientWebSocket;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import io.reactivex.Single;
import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;

@ClientWebSocket("/context/chat/{topic}/{username}")
public abstract class ContextAwareChatClientWebSocket implements AutoCloseable {

    private WebSocketSession session;
    private HttpRequest request;
    private String topic;
    private String username;
    private Collection<String> replies = new ConcurrentLinkedQueue<>();
    private String subProtocol;

    @OnOpen
    public void onOpen(String topic, String username, WebSocketSession session, HttpRequest request) {
        this.topic = topic;
        this.username = username;
        this.session = session;
        this.request = request;
        this.subProtocol = session.getSubprotocol().orElse(null);
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

    public HttpRequest getRequest() {
        return request;
    }

    @OnMessage
    public void onMessage(String message) {
        replies.add(message);
    }

    public abstract void send(String message);

    public abstract Future<String> sendAsync(String message);

    public abstract Single<String> sendRx(String message);

    public String getSubProtocol() {
        return subProtocol;
    }
}
