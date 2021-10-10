package io.micronaut.docs.http.server.netty.websocket

import io.micronaut.websocket.annotation.ClientWebSocket
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.reactivex.Single

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Future

@ClientWebSocket("/pojo/chat/{topic}/{username}")
abstract class PojoChatClientWebSocket implements AutoCloseable {

    private String topic
    private String username
    private Collection<Message> replies = new ConcurrentLinkedQueue<>()

    @OnOpen
    void onOpen(String topic, String username) {
        this.topic = topic
        this.username = username
    }

    String getTopic() {
        return topic
    }

    String getUsername() {
        return username
    }

    Collection<Message> getReplies() {
        return replies
    }

    @OnMessage
    void onMessage(
            Message message) {
        System.out.println("Client received message = " + message)
        replies.add(message)
    }

    abstract void send(Message message)

    abstract Future<Message> sendAsync(Message message)

    abstract Single<Message> sendRx(Message message)
}
