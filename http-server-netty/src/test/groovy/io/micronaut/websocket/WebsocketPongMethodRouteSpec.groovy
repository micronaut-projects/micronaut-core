package io.micronaut.websocket

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.RouteCondition
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.websocket.annotation.ClientWebSocket
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.ServerWebSocket
import io.micronaut.websocket.exceptions.WebSocketClientException
import jakarta.inject.Inject
import reactor.core.publisher.Flux
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.concurrent.CopyOnWriteArrayList

/**
 * The route of a {@code @ServerWebSocket} has the annotations of its {@code @OnMessage} method of
 * the messages, and not of the one of the pongs, though that one is declared first.
 */
@Property(name = "spec.name", value = "WebsocketPongMethodRouteSpec")
@MicronautTest
class WebsocketPongMethodRouteSpec extends Specification {

    @Inject
    EmbeddedServer embeddedServer

    void "the condition of the message method applies to the upgrade request, not the pong method declared before it"() {
        given:
        WebSocketClient wsClient = embeddedServer.applicationContext.createBean(WebSocketClient, embeddedServer.getURL())

        when:
        Flux.from(wsClient.connect(RecordingClientWebSocket, HttpRequest.GET("/pong-first"))).blockFirst()

        then:
        WebSocketClientException e = thrown()
        e.message.contains("404 Not Found")

        when:
        RecordingClientWebSocket socket = Flux.from(wsClient.connect(RecordingClientWebSocket, HttpRequest.GET("/pong-first").header("X-WebSocket", "yes"))).blockFirst()
        socket.send("hello")

        then:
        new PollingConditions(timeout: 5).eventually {
            socket.messages == ["echo hello"]
        }

        cleanup:
        socket?.close()
    }

    @Requires(property = "spec.name", value = "WebsocketPongMethodRouteSpec")
    @ServerWebSocket("/pong-first")
    static class PongFirstServerWebSocket {
        @OnMessage
        void onPong(WebSocketPongMessage pong, WebSocketSession session) {
            // intentionally empty: the spec only checks which method gives the route its annotations
        }

        @OnMessage
        @RouteCondition("#{request.headers.getFirst('X-WebSocket').orElse(null) == 'yes'}")
        void onMessage(String message, WebSocketSession session) {
            session.sendSync("echo " + message)
        }
    }

    @Requires(property = "spec.name", value = "WebsocketPongMethodRouteSpec")
    @ClientWebSocket
    static abstract class RecordingClientWebSocket implements AutoCloseable {
        final List<String> messages = new CopyOnWriteArrayList<>()

        @OnMessage
        void onMessage(String message) {
            messages.add(message)
        }

        abstract void send(String message)
    }
}
