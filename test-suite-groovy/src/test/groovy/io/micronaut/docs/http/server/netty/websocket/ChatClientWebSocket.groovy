package io.micronaut.docs.http.server.netty.websocket

// tag::imports[]

import io.micronaut.http.HttpRequest
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.ClientWebSocket
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.reactivex.Single

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Future

// end::imports[]

// tag::class[]
@ClientWebSocket("/chat/{topic}/{username}") // <1>
abstract class ChatClientWebSocket implements AutoCloseable { // <2>

    private WebSocketSession session
    private HttpRequest request
    private String topic
    private String username
    private Collection<String> replies = new ConcurrentLinkedQueue<>()

    @OnOpen
    void onOpen(String topic, String username, WebSocketSession session, HttpRequest request) { // <3>
        this.topic = topic
        this.username = username
        this.session = session
        this.request = request
    }

    String getTopic() {
        topic
    }

    String getUsername() {
        username
    }

    Collection<String> getReplies() {
        replies
    }

    WebSocketSession getSession() {
        session
    }

    HttpRequest getRequest() {
        request
    }

    @OnMessage
    void onMessage(
            String message) {
        replies.add(message) // <4>
    }

// end::class[]
    abstract void send(String message)

    abstract Future<String> sendAsync(String message)

    abstract Single<String> sendRx(String message)

}
