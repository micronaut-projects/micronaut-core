package io.micronaut.websocket

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.RouteCondition
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.web.router.builder.HttpRouteBuilder
import io.micronaut.web.router.builder.HttpRoutes
import io.micronaut.websocket.annotation.ClientWebSocket
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.micronaut.websocket.annotation.ServerWebSocket
import io.micronaut.websocket.exceptions.WebSocketClientException
import jakarta.inject.Inject
import jakarta.inject.Singleton
import reactor.core.publisher.Flux
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.concurrent.CopyOnWriteArrayList

/**
 * A WebSocket and a catch-all route for plain requests at the same path, like the push of Vaadin,
 * which falls back to long polling with plain requests to the path of its WebSocket.
 */
@Property(name = "spec.name", value = "WebsocketPlainRequestRoutingSpec")
@MicronautTest
class WebsocketPlainRequestRoutingSpec extends Specification {

    @Inject
    EmbeddedServer embeddedServer

    @Inject
    @Client("/")
    HttpClient httpClient

    void "an upgrade request opens the WebSocket and a plain request reaches the catch-all route"() {
        given:
        WebSocketClient wsClient = embeddedServer.applicationContext.createBean(WebSocketClient, embeddedServer.getURL())

        when:
        RecordingClientWebSocket socket = Flux.from(wsClient.connect(RecordingClientWebSocket, HttpRequest.GET("/plain-routing/push"))).blockFirst()
        socket.send("hello")

        then:
        new PollingConditions(timeout: 5).eventually {
            socket.messages == ["open", "echo hello"]
        }

        when:
        String body = httpClient.toBlocking().retrieve(HttpRequest.GET("/plain-routing/push"))

        then:
        body == "fallback push"

        cleanup:
        socket?.close()
    }

    void "a WebSocket with only an @OnMessage method opens"() {
        given:
        WebSocketClient wsClient = embeddedServer.applicationContext.createBean(WebSocketClient, embeddedServer.getURL())

        when:
        RecordingClientWebSocket socket = Flux.from(wsClient.connect(RecordingClientWebSocket, HttpRequest.GET("/plain-only/message-only"))).blockFirst()
        socket.send("hello")

        then:
        new PollingConditions(timeout: 5).eventually {
            socket.messages == ["echo hello"]
        }

        cleanup:
        socket?.close()
    }

    void "the conditions of the route of a WebSocket apply to the upgrade request"() {
        given:
        WebSocketClient wsClient = embeddedServer.applicationContext.createBean(WebSocketClient, embeddedServer.getURL())

        when:
        Flux.from(wsClient.connect(RecordingClientWebSocket, HttpRequest.GET("/plain-only/conditional"))).blockFirst()

        then:
        WebSocketClientException e = thrown()
        e.message.contains("404 Not Found")

        when:
        RecordingClientWebSocket socket = Flux.from(wsClient.connect(RecordingClientWebSocket, HttpRequest.GET("/plain-only/conditional").header("X-WebSocket", "yes"))).blockFirst()

        then:
        new PollingConditions(timeout: 5).eventually {
            socket.messages == ["open"]
        }

        cleanup:
        socket?.close()
    }

    void "a plain request to a WebSocket without another route is a bad request"() {
        when:
        httpClient.toBlocking().exchange(HttpRequest.GET("/plain-only/message-only"), String)

        then:
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.BAD_REQUEST
    }

    @Requires(property = "spec.name", value = "WebsocketPlainRequestRoutingSpec")
    @ServerWebSocket("/plain-routing/push")
    static class PushServerWebSocket {
        @OnOpen
        void onOpen(WebSocketSession session) {
            session.sendSync("open")
        }

        @OnMessage
        void onMessage(String message, WebSocketSession session) {
            session.sendSync("echo " + message)
        }
    }

    @Requires(property = "spec.name", value = "WebsocketPlainRequestRoutingSpec")
    @ServerWebSocket("/plain-only/message-only")
    static class MessageOnlyServerWebSocket {
        @OnMessage
        void onMessage(String message, WebSocketSession session) {
            session.sendSync("echo " + message)
        }
    }

    @Requires(property = "spec.name", value = "WebsocketPlainRequestRoutingSpec")
    @ServerWebSocket("/plain-only/conditional")
    static class ConditionalServerWebSocket {
        @OnOpen
        @RouteCondition("#{request.headers.getFirst('X-WebSocket').orElse(null) == 'yes'}")
        void onOpen(WebSocketSession session) {
            session.sendSync("open")
        }

        @OnMessage
        void onMessage(String message, WebSocketSession session) {
        }
    }

    @Requires(property = "spec.name", value = "WebsocketPlainRequestRoutingSpec")
    @Singleton
    static class FallbackRoutes implements HttpRoutes {
        @Override
        void routes(HttpRouteBuilder routes) {
            routes.GET("/plain-routing/{+path}")
                .handle({ request, pathVariables -> HttpResponse.ok("fallback " + pathVariables.getString("path")).contentType(MediaType.TEXT_PLAIN_TYPE) })
        }
    }

    @Requires(property = "spec.name", value = "WebsocketPlainRequestRoutingSpec")
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
