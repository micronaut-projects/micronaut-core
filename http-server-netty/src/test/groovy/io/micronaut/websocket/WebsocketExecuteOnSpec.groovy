package io.micronaut.websocket

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.LoomSupport
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.websocket.annotation.*
import jakarta.inject.Inject
import org.reactivestreams.Publisher
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Unroll
import spock.util.concurrent.PollingConditions

import java.util.concurrent.Future
import java.util.function.Predicate
import java.util.function.Supplier
import java.util.stream.Collectors

@Property(name = "spec.name", value = "WebsocketExecuteOnSpec")
@MicronautTest
class WebsocketExecuteOnSpec extends Specification {

    static final Logger LOG = LoggerFactory.getLogger(WebsocketExecuteOnSpec.class)

    @Inject
    EmbeddedServer embeddedServer

    @Unroll
    void "#type websocket server methods can run outside of the event loop with ExecuteOn"() {
        given:
        WebSocketClient wsClient = embeddedServer.applicationContext.createBean(WebSocketClient.class, embeddedServer.getURL())
        String threadName = (LoomSupport.isSupported() ? "virtual" : TaskExecutors.IO) + "-executor"
        String expectedJoined = "joined on thread " + threadName
        String expectedEcho = "Hello from thread " + threadName

        expect:
        wsClient

        when:
        EchoClientWebSocket echoClientWebSocket = Flux.from(wsClient.connect(EchoClientWebSocket, "/echo/${type}")).blockFirst()

        then:
        noExceptionThrown()
        new PollingConditions().eventually {
            echoClientWebSocket.receivedMessages() == [expectedJoined]
        }

        when:
        echoClientWebSocket.send('Hello')

        then:
        new PollingConditions().eventually {
            echoClientWebSocket.receivedMessages() == [expectedJoined, expectedEcho]
        }

        cleanup:
        echoClientWebSocket.close()

        where:
        type        | _
        "sync"      | _
        "reactive"  | _
        "async"     | _
    }

    @Requires(property = "spec.name", value = "WebsocketExecuteOnSpec")
    @ServerWebSocket("/echo/sync")
    @ExecuteOn(TaskExecutors.BLOCKING)
    static class SynchronousEchoServerWebSocket {
        public static final String JOINED = "joined on thread %s"
        public static final String DISCONNECTED = "disconnected on thread %s"
        public static final String ECHO = "%s from thread %s"

        @Inject
        WebSocketBroadcaster broadcaster

        @OnOpen
        void onOpen(WebSocketSession session) {
            broadcaster.broadcastSync(JOINED.formatted(Thread.currentThread().getName()), isValid(session))
        }

        @OnMessage
        void onMessage(String message, WebSocketSession session) {
            broadcaster.broadcastSync(ECHO.formatted(message, Thread.currentThread().getName()), isValid(session))
        }

        @OnClose
        void onClose(WebSocketSession session) {
            broadcaster.broadcastSync(DISCONNECTED.formatted(Thread.currentThread().getName()), isValid(session))
        }

        private static Predicate<WebSocketSession> isValid(WebSocketSession session) {
            return { s -> s == session }
        }
    }

    @Requires(property = "spec.name", value = "WebsocketExecuteOnSpec")
    @ServerWebSocket("/echo/reactive")
    @ExecuteOn(TaskExecutors.BLOCKING)
    static class ReactiveEchoServerWebSocket {
        public static final String JOINED = "joined on thread %s"
        public static final String DISCONNECTED = "disconnected on thread %s"
        public static final String ECHO = " from thread %s"

        @Inject
        WebSocketBroadcaster broadcaster

        Supplier<String> formatMessage(String message) {
            () -> message.formatted(Thread.currentThread().getName())
        }

        @OnOpen
        Publisher<String> onOpen(WebSocketSession session) {
            Mono.fromSupplier(formatMessage(JOINED))
                    .flatMap(message -> Mono.from(broadcaster.broadcast(message)))
        }

        @OnMessage
        Publisher<String> onMessage(String message, WebSocketSession session) {
            Mono.fromSupplier(formatMessage(message + ECHO))
                    .flatMap(m -> Mono.from(broadcaster.broadcast(m)))
        }

        @OnClose
        Publisher<String> onClose(WebSocketSession session) {
            Mono.just(session)
                .flatMap(s -> {
                    LOG.info(DISCONNECTED.formatted(Thread.currentThread().getName()))
                    return Mono.just("closed")
                })
        }
    }

    @Requires(property = "spec.name", value = "WebsocketExecuteOnSpec")
    @ServerWebSocket("/echo/async")
    @ExecuteOn(TaskExecutors.BLOCKING)
    static class AsyncEchoServerWebSocket {
        public static final String JOINED = "joined on thread %s"
        public static final String DISCONNECTED = "disconnected on thread %s"
        public static final String ECHO = " from thread %s"

        @Inject
        WebSocketBroadcaster broadcaster

        Supplier<String> formatMessage(String message) {
            () -> message.formatted(Thread.currentThread().getName())
        }

        @OnOpen
        Future<String> onOpen(WebSocketSession session) {
            Mono.fromSupplier(formatMessage(JOINED))
                    .flatMap(message -> Mono.from(broadcaster.broadcast(message))).toFuture();
        }

        @OnMessage
        Future<String> onMessage(String message, WebSocketSession session) {
            Mono.fromSupplier(formatMessage(message + ECHO))
                    .flatMap(m -> Mono.from(broadcaster.broadcast(m))).toFuture()
        }

        @OnClose
        Future<String> onClose(WebSocketSession session) {
            Mono.just(session)
                    .flatMap(s -> {
                        LOG.info(DISCONNECTED.formatted(Thread.currentThread().getName()))
                        return Mono.just("closed")
                    }).toFuture()
        }
    }

    @Requires(property = "spec.name", value = "WebsocketExecuteOnSpec")
    @ClientWebSocket
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
                    .map(str -> str.substring(0, str.length()-(1)).replace("-thread-", ""))
                    .collect(Collectors.toList())
        }
    }
}
