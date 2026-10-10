package io.micronaut.websocket

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.RouteCondition
import io.micronaut.http.annotation.ServerFilter
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
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
import spock.lang.Timeout
import spock.util.concurrent.PollingConditions

import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList

@Property(name = "spec.name", value = "WebsocketRouteLookupErrorSpec")
@MicronautTest
class WebsocketRouteLookupErrorSpec extends Specification {

    @Inject
    EmbeddedServer embeddedServer

    @Inject
    @Client("/")
    HttpClient httpClient

    @Timeout(30)
    void "a route condition that fails answers the upgrade request with an error"() {
        given:
        WebSocketClient wsClient = embeddedServer.applicationContext.createBean(WebSocketClient, embeddedServer.getURL())

        when:
        Flux.from(wsClient.connect(RecordingClientWebSocket, HttpRequest.GET("/route-lookup/failing")))
            .blockFirst(Duration.ofSeconds(10))

        then:
        WebSocketClientException e = thrown()
        e.message.contains("500")
    }

    void "a plain request does not evaluate the route condition of the WebSocket"() {
        expect:
        httpClient.toBlocking().retrieve(HttpRequest.GET("/route-lookup/failing")) == "fallback"
    }

    void "the route of the WebSocket is matched after the pre-matching filters"() {
        given:
        WebSocketClient wsClient = embeddedServer.applicationContext.createBean(WebSocketClient, embeddedServer.getURL())

        when:
        RecordingClientWebSocket socket = Flux.from(wsClient.connect(RecordingClientWebSocket, HttpRequest.GET("/route-lookup/filtered")))
            .blockFirst(Duration.ofSeconds(10))
        socket.send("hello")

        then:
        new PollingConditions(timeout: 5).eventually {
            socket.messages == ["open", "echo hello"]
        }

        cleanup:
        socket?.close()
    }

    @Requires(property = "spec.name", value = "WebsocketRouteLookupErrorSpec")
    @ServerWebSocket("/route-lookup/failing")
    static class FailingConditionWebSocket {
        @OnOpen
        @RouteCondition("#{request.headers.getFirst('X-Missing').get() == 'yes'}")
        void onOpen(WebSocketSession session) {
            session.sendSync("open")
        }

        @OnMessage
        void onMessage(String message, WebSocketSession session) {
            session.sendSync("echo " + message)
        }
    }

    @Requires(property = "spec.name", value = "WebsocketRouteLookupErrorSpec")
    @Controller("/route-lookup")
    static class FallbackController {
        @Get(value = "/failing", produces = MediaType.TEXT_PLAIN)
        String fallback() {
            "fallback"
        }
    }

    @Requires(property = "spec.name", value = "WebsocketRouteLookupErrorSpec")
    @ServerWebSocket("/route-lookup/filtered")
    static class FilteredWebSocket {
        @OnOpen
        @RouteCondition("#{request.headers.getFirst('X-Pre-Matched').orElse(null) == 'yes'}")
        void onOpen(WebSocketSession session) {
            session.sendSync("open")
        }

        @OnMessage
        void onMessage(String message, WebSocketSession session) {
            session.sendSync("echo " + message)
        }
    }

    @Requires(property = "spec.name", value = "WebsocketRouteLookupErrorSpec")
    @ServerFilter("/route-lookup/filtered")
    static class PreMatchingHeaderFilter {
        @PreMatching
        @RequestFilter
        HttpRequest<?> addHeader(HttpRequest<?> request) {
            request.mutate().header("X-Pre-Matched", "yes")
        }
    }

    @Requires(property = "spec.name", value = "WebsocketRouteLookupErrorSpec")
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
