package io.micronaut.docs.http.server.netty.websocket

//tag::clazz[]

import io.micronaut.websocket.WebSocketBroadcaster
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.OnClose
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.micronaut.websocket.annotation.ServerWebSocket

import java.util.function.Predicate

@ServerWebSocket("/chat/{topic}/{username}") // <1>
class ChatServerWebSocket(private val broadcaster: WebSocketBroadcaster) {

    @OnOpen // <2>
    fun onOpen(topic: String, username: String, session: WebSocketSession) {
        val msg = "[$username] Joined!"
        broadcaster.broadcastSync(msg, isValid(topic, session))
    }

    @OnMessage // <3>
    fun onMessage(
            topic: String,
            username: String,
            message: String,
            session: WebSocketSession) {
        val msg = "[$username] $message"
        broadcaster.broadcastSync(msg, isValid(topic, session)) // <4>
    }

    @OnClose // <5>
    fun onClose(
            topic: String,
            username: String,
            session: WebSocketSession) {
        val msg = "[$username] Disconnected!"
        broadcaster.broadcastSync(msg, isValid(topic, session))
    }

    private fun isValid(topic: String, session: WebSocketSession): Predicate<WebSocketSession> {
        return Predicate<WebSocketSession>{ s -> (s !== session && topic.equals(s.getUriVariables().get("topic", String::class.java, null), ignoreCase = true)) }
    }
}
//end::clazz[]
