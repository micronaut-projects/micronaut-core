package io.micronaut.websocket

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.LoomSupport
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.websocket.annotation.ClientWebSocket
import io.micronaut.websocket.annotation.OnClose
import io.micronaut.websocket.annotation.OnError
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.micronaut.websocket.annotation.ServerWebSocket
import jakarta.inject.Inject
import org.reactivestreams.Publisher
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Unroll
import spock.util.concurrent.PollingConditions

import java.util.concurrent.CompletableFuture
import java.util.function.Predicate
import java.util.function.Supplier
import java.util.stream.Collectors

@Property(name = "spec.name", value = "WebsocketExecuteOnSpec")
@MicronautTest
class WebsocketExecuteOnSpec extends Specification {

    static final Logger LOG = LoggerFactory.getLogger(WebsocketExecuteOnSpec.class)

    static final String ERROR_MESSAGE = "error"
    static final String JOINED = "joined on thread %s"
    static final String DISCONNECTED = "disconnected on thread %s"
    static final String ECHO = " from thread %s"
    static final String ERROR_RESPONSE = "error handled on thread %s"

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

    @Unroll
    void "#type websocket server handler can handle errors with ExecuteOn"() {
        given:
        WebSocketClient wsClient = embeddedServer.applicationContext.createBean(WebSocketClient.class, embeddedServer.getURL())
        String threadName = (LoomSupport.isSupported() ? "virtual" : TaskExecutors.IO) + "-executor"
        String expectedJoined = "joined on thread " + threadName
        String expectedError = "error handled on thread " + threadName
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
        echoClientWebSocket.send(ERROR_MESSAGE)

        then:
        new PollingConditions().eventually {
            echoClientWebSocket.receivedMessages() == [expectedJoined, expectedError]
        }

        when:
        echoClientWebSocket.send('Hello')

        then:
        new PollingConditions().eventually {
            echoClientWebSocket.receivedMessages() == [expectedJoined, expectedError, expectedEcho]
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

        @Inject
        WebSocketBroadcaster broadcaster

        @OnOpen
        void onOpen(WebSocketSession session) {
            broadcaster.broadcastSync(JOINED.formatted(Thread.currentThread().getName()), isValid(session))
        }

        @OnMessage
        void onMessage(String message, WebSocketSession session) {
            if (message == ERROR_MESSAGE) {
                throw new IllegalStateException("this should be handled")
            }
            broadcaster.broadcastSync((message + ECHO).formatted(Thread.currentThread().getName()), isValid(session))
        }

        @OnClose
        void onClose(WebSocketSession session) {
            broadcaster.broadcastSync(DISCONNECTED.formatted(Thread.currentThread().getName()), isValid(session))
        }

        @OnError
        void handleError(IllegalStateException ex) {
            LOG.info("Handling error from error handler")
            broadcaster.broadcastSync(ERROR_RESPONSE.formatted(Thread.currentThread().getName()))
        }

        private static Predicate<WebSocketSession> isValid(WebSocketSession session) {
            return { s -> s == session }
        }
    }

    @Requires(property = "spec.name", value = "WebsocketExecuteOnSpec")
    @ServerWebSocket("/echo/reactive")
    @ExecuteOn(TaskExecutors.BLOCKING)
    static class ReactiveEchoServerWebSocket {

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
            if (message == ERROR_MESSAGE) {
                return Mono.error(new IllegalStateException("this should be handled"))
            }
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

        @OnError
        Publisher<?> handleError(IllegalStateException ex) {
            LOG.info("Handling error from error handler")
            Mono.fromSupplier(() -> ERROR_RESPONSE.formatted(Thread.currentThread().getName()))
                    .flatMap(m -> Mono.from(broadcaster.broadcast(m))).then(Mono.empty())
        }
    }

    @Requires(property = "spec.name", value = "WebsocketExecuteOnSpec")
    @ServerWebSocket("/echo/async")
    @ExecuteOn(TaskExecutors.BLOCKING)
    static class AsyncEchoServerWebSocket {

        @Inject
        WebSocketBroadcaster broadcaster

        Supplier<String> formatMessage(String message) {
            () -> message.formatted(Thread.currentThread().getName())
        }

        @OnOpen
        CompletableFuture<String> onOpen(WebSocketSession session) {
            Mono.fromSupplier(formatMessage(JOINED))
                    .flatMap(message -> Mono.from(broadcaster.broadcast(message))).toFuture();
        }

        @OnMessage
        CompletableFuture<String> onMessage(String message, WebSocketSession session) {
            if (message == ERROR_MESSAGE) {
                return Mono.error(new IllegalStateException("this should be handled")).toFuture()
            }
            Mono.fromSupplier(formatMessage(message + ECHO))
                    .flatMap(m -> Mono.from(broadcaster.broadcast(m))).toFuture()
        }

        @OnClose
        CompletableFuture<String> onClose(WebSocketSession session) {
            Mono.just(session)
                    .flatMap(s -> {
                        LOG.info(DISCONNECTED.formatted(Thread.currentThread().getName()))
                        return Mono.just("closed")
                    }).toFuture()
        }

        @OnError
        CompletableFuture<?> handleError(IllegalStateException ex) {
            LOG.info("Handling error from error handler")
            Mono.fromSupplier(() -> ERROR_RESPONSE.formatted(Thread.currentThread().getName()))
                    .flatMap(m -> Mono.from(broadcaster.broadcast(m))).then(Mono.empty()).toFuture()
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
                    .map(str -> str.replaceAll("-?\\d", "").replace("-thread", ""))
                    .collect(Collectors.toList())
        }
    }
}
