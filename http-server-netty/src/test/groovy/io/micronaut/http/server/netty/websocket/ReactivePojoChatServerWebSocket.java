package io.micronaut.http.server.netty.websocket;

import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.OnClose;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import io.micronaut.websocket.annotation.ServerWebSocket;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;

import java.util.Set;
import java.util.stream.Collectors;

@ServerWebSocket("/pojo/chat/{topic}/{username}")
public class ReactivePojoChatServerWebSocket {

    @OnOpen
    public Publisher<Message> onOpen(String topic, String username, WebSocketSession session) {
        String text = "[" + username + "] Joined!";
        return buildMessagePublisher(topic, session, text);
    }

    @OnMessage
    public Publisher<Message> onMessage(
            String topic,
            String username,
            Message message,
            WebSocketSession session) {

        String text = "[" + username + "] " + message.getText();
        return buildMessagePublisher(topic, session, text);
    }

    @OnClose
    public Publisher<Message> onClose(
            String topic,
            String username,
            WebSocketSession session) {
        String text = "[" + username + "] Disconnected!";
        return buildMessagePublisher(topic, session, text);
    }

    private Publisher<Message> buildMessagePublisher(String topic, WebSocketSession session, String text) {
        Set<? extends WebSocketSession> openSessions = session.getOpenSessions();
        Set<Publisher<Message>> messagePublishers = openSessions.stream()
                .filter((openSession) -> isValid(topic, session, openSession))
                .map((s) -> {
                    Message newMessage = new Message();
                    newMessage.setText(text);
                    return s.send(newMessage);
                }).collect(Collectors.toSet());

        return Flowable.concat(messagePublishers);
    }

    private boolean isValid(String topic, WebSocketSession session, WebSocketSession openSession) {
        return openSession != session && topic.equalsIgnoreCase(openSession.getUriVariables().get("topic", String.class).orElse(null));
    }
}
