package io.micronaut.security.token.websockets

import io.micronaut.context.annotation.Requires
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.ClientWebSocket
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Requires(property = "spec.name", value = "websockets-on-open-header")
@ClientWebSocket("/echo")
abstract class EchoClientWebSocket implements AutoCloseable {

    static final String RECEIVED = 'RECEIVED:'

    private static final Logger LOG = LoggerFactory.getLogger(EchoClientWebSocket.class)

    private WebSocketSession session
    private List<String> replies = new ArrayList<>()

    @OnOpen
    void onOpen(WebSocketSession session) {
        this.session = session
    }
    List<Map> getReplies() {
        return replies
    }

    @OnMessage
    void onMessage(String message) {
        replies.add(RECEIVED + message)
    }

    abstract void send(String message)

    List<String> receivedMessages() {
        filterMessagesByType(RECEIVED)
    }

    List<String> filterMessagesByType(String type) {
        replies.findAll { String str ->
            str.contains(type)
        }.collect { String str ->
            str.replaceAll(type, '')
        }
    }
}
