package io.micronaut.websocket

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.FilterMatcher
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.RouteCondition
import io.micronaut.http.annotation.ServerFilter
import io.micronaut.http.server.annotation.PreMatching
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.websocket.annotation.ClientWebSocket
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.micronaut.websocket.annotation.ServerWebSocket
import io.micronaut.websocket.exceptions.WebSocketClientException
import jakarta.inject.Inject
import reactor.core.publisher.Flux
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The route of a WebSocket targets its {@link OnOpen} method, and has the annotations of both its
 * {@link OnOpen} and its {@link OnMessage} method.
 */
@Property(name = "spec.name", value = "WebsocketRouteAnnotationsSpec")
@MicronautTest
class WebsocketRouteAnnotationsSpec extends Specification {

    @Inject
    EmbeddedServer embeddedServer

    @Inject
    GuardFilter guardFilter

    void "the route condition of the @OnMessage method applies to the upgrade request"() {
        given:
        WebSocketClient wsClient = embeddedServer.applicationContext.createBean(WebSocketClient, embeddedServer.getURL())

        when:
        Flux.from(wsClient.connect(RecordingClientWebSocket, HttpRequest.GET("/route-annotations/guarded")))
            .blockFirst(Duration.ofSeconds(10))

        then:
        WebSocketClientException e = thrown()
        e.message.contains("404")
    }

    void "the route conditions of both methods apply to the upgrade request"() {
        given:
        WebSocketClient wsClient = embeddedServer.applicationContext.createBean(WebSocketClient, embeddedServer.getURL())

        when:
        Flux.from(wsClient.connect(RecordingClientWebSocket, HttpRequest.GET("/route-annotations/guarded").header("X-Message", "yes")))
            .blockFirst(Duration.ofSeconds(10))

        then:
        WebSocketClientException e = thrown()
        e.message.contains("404")

        when:
        Flux.from(wsClient.connect(RecordingClientWebSocket, HttpRequest.GET("/route-annotations/guarded").header("X-Open", "yes")))
            .blockFirst(Duration.ofSeconds(10))

        then:
        e = thrown()
        e.message.contains("404")
    }

    void "a filter that matches an annotation of the @OnMessage method runs for the upgrade request"() {
        given:
        WebSocketClient wsClient = embeddedServer.applicationContext.createBean(WebSocketClient, embeddedServer.getURL())
        guardFilter.paths.clear()

        when:
        RecordingClientWebSocket socket = Flux.from(wsClient.connect(RecordingClientWebSocket, HttpRequest.GET("/route-annotations/guarded")
            .header("X-Open", "yes")
            .header("X-Message", "yes")))
            .blockFirst(Duration.ofSeconds(10))
        socket.send("hello")

        then:
        new PollingConditions(timeout: 5).eventually {
            socket.messages == ["open", "echo hello"]
        }
        guardFilter.paths == ["/route-annotations/guarded"]

        cleanup:
        socket?.close()
    }

    void "a pre-matching filter rewrites the path the route of a WebSocket is matched with"() {
        given:
        WebSocketClient wsClient = embeddedServer.applicationContext.createBean(WebSocketClient, embeddedServer.getURL())

        when:
        RecordingClientWebSocket socket = Flux.from(wsClient.connect(RecordingClientWebSocket, HttpRequest.GET("/route-annotations/old")))
            .blockFirst(Duration.ofSeconds(10))
        socket.send("hello")

        then:
        new PollingConditions(timeout: 5).eventually {
            socket.messages == ["open rewritten", "echo hello"]
        }

        cleanup:
        socket?.close()
    }

    @Requires(property = "spec.name", value = "WebsocketRouteAnnotationsSpec")
    @ServerWebSocket("/route-annotations/guarded")
    static class GuardedWebSocket {
        @OnOpen
        @RouteCondition("#{request.headers.getFirst('X-Open').orElse(null) == 'yes'}")
        void onOpen(WebSocketSession session) {
            session.sendSync("open")
        }

        @OnMessage
        @Guarded
        @RouteCondition("#{request.headers.getFirst('X-Message').orElse(null) == 'yes'}")
        void onMessage(String message, WebSocketSession session) {
            session.sendSync("echo " + message)
        }
    }

    @Requires(property = "spec.name", value = "WebsocketRouteAnnotationsSpec")
    @ServerWebSocket("/route-annotations/rewritten")
    static class RewrittenWebSocket {
        @OnOpen
        void onOpen(WebSocketSession session) {
            session.sendSync("open rewritten")
        }

        @OnMessage
        void onMessage(String message, WebSocketSession session) {
            session.sendSync("echo " + message)
        }
    }

    @Requires(property = "spec.name", value = "WebsocketRouteAnnotationsSpec")
    @ServerFilter("/route-annotations/old")
    static class RewriteFilter {
        @PreMatching
        @RequestFilter
        HttpRequest<?> rewrite(HttpRequest<?> request) {
            request.mutate().uri(URI.create("/route-annotations/rewritten"))
        }
    }

    @Requires(property = "spec.name", value = "WebsocketRouteAnnotationsSpec")
    @Guarded
    @ServerFilter("/route-annotations/**")
    static class GuardFilter {
        final List<String> paths = new CopyOnWriteArrayList<>()

        @RequestFilter
        void record(HttpRequest<?> request) {
            paths.add(request.path)
        }
    }

    @Requires(property = "spec.name", value = "WebsocketRouteAnnotationsSpec")
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

@FilterMatcher
@interface Guarded {
}
