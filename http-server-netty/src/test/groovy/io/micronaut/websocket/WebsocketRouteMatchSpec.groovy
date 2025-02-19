package io.micronaut.websocket

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Filter
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.web.router.RouteAttributes
import io.micronaut.web.router.RouteMatch
import io.micronaut.websocket.annotation.ClientWebSocket
import io.micronaut.websocket.annotation.OnClose
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.micronaut.websocket.annotation.ServerWebSocket
import jakarta.inject.Inject
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.function.Predicate
import java.util.stream.Collectors

@Property(name = "spec.name", value = "WebsocketRouteMatchSpec")
@MicronautTest
class WebsocketRouteMatchSpec extends Specification {

    @Inject
    EmbeddedServer embeddedServer

    void "request attributes contains a route match for WebSocketServer"() {
        given:
        WebSocketClient wsClient = embeddedServer.applicationContext.createBean(WebSocketClient.class, embeddedServer.getURL())

        expect:
        wsClient

        when:
        MutableHttpRequest<?> request = HttpRequest.GET("/echo")
        EchoClientWebSocket echoClientWebSocket = Flux.from(wsClient.connect(EchoClientWebSocket, request)).blockFirst()

        then:
        noExceptionThrown()
        new PollingConditions().eventually {
            echoClientWebSocket.receivedMessages() == ['joined!']
        }

        when:
        echoClientWebSocket.send('Hello')

        then:
        new PollingConditions().eventually {
            echoClientWebSocket.receivedMessages() == ['joined!', 'Hello']
        }

        cleanup:
        echoClientWebSocket.close()
    }

    @Requires(property = "spec.name", value = "WebsocketRouteMatchSpec")
    @ServerWebSocket("/echo")
    static class EchoServerWebSocket {
        public static final String JOINED = "joined!"
        public static final String DISCONNECTED = "Disconnected!"

        @Inject
        WebSocketBroadcaster broadcaster

        @OnOpen
        void onOpen(WebSocketSession session) {
            broadcaster.broadcastSync(JOINED, isValid(session))
        }

        @OnMessage
        void onMessage(String message, WebSocketSession session) {
            broadcaster.broadcastSync(message, isValid(session))
        }

        @OnClose
        void onClose(WebSocketSession session) {
            broadcaster.broadcastSync(DISCONNECTED, isValid(session))
        }

        private static Predicate<WebSocketSession> isValid(WebSocketSession session) {
            return { s -> s == session }
        }
    }

    @Requires(property = "spec.name", value = "WebsocketRouteMatchSpec")
    @Filter(Filter.MATCH_ALL_PATTERN)
    static class SecurityFilter implements HttpServerFilter {
        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            RouteMatch<?> routeMatch = RouteAttributes.getRouteMatch(request).orElse(null)
            routeMatch != null ? chain.proceed(request) : Mono.just(HttpResponse.serverError())
        }
    }

    @Requires(property = "spec.name", value = "WebsocketRouteMatchSpec")
    @ClientWebSocket("/echo")
    static abstract class EchoClientWebSocket implements AutoCloseable {

        static final String RECEIVED = "RECEIVED:"

        private WebSocketSession session
        private List<String> replies = new ArrayList<>()

        @OnOpen
        void onOpen(WebSocketSession session) {
            this.session = session
        }
        List<String> getReplies() {
            return replies
        }

        @OnMessage
        void onMessage(String message) {
            replies.add(RECEIVED + message)
        }

        abstract void send(String message)

        List<String> receivedMessages() {
            return filterMessagesByType(RECEIVED)
        }

        List<String> filterMessagesByType(String type) {
            replies.stream()
                    .filter(str -> str.contains(type))
                    .map(str -> str.replaceAll(type, ""))
                    .collect(Collectors.toList())
        }
    }
}
