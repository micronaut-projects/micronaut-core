package io.micronaut.http.server.netty.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.context.ServerRequestContext
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.WebSocketClient
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.ClientWebSocket
import io.micronaut.websocket.annotation.OnError
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.micronaut.websocket.annotation.ServerWebSocket
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.time.Duration
import java.util.concurrent.ConcurrentLinkedQueue

class WebSocketHandlerResultSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'WebSocketHandlerResultSpec'])

    @Shared
    @AutoCleanup
    WebSocketClient wsClient = server.applicationContext.createBean(WebSocketClient, server.URI)

    private List<String> exchange(String mode, List<String> messages, int expected) {
        ResultClient client = Flux.from(wsClient.connect(ResultClient, "/ws/result/" + mode)).blockFirst()
        messages.each { client.session.sendSync(it) }
        new PollingConditions(timeout: 10).eventually {
            assert client.received.size() >= expected
        }
        sleep(100)
        List<String> result = new ArrayList<>(client.received)
        client.session.close()
        return result
    }

    void "synchronous handler results are processed in order"() {
        expect:
        exchange("sync", ["1", "2", "3"], 3) == ["1", "2", "3"]
    }

    void "delayed Mono results completed on another thread are all processed"() {
        expect:
        exchange("mono", ["1", "2", "3"], 3).sort() == ["1", "2", "3"]
    }

    void "Flux results are fully consumed"() {
        when:
        def received = exchange("flux", ["1", "2"], 4)

        then:
        received.sort() == ["1a", "1b", "2a", "2b"]
        received.indexOf("1a") < received.indexOf("1b")
        received.indexOf("2a") < received.indexOf("2b")
    }

    void "request context is available in the handler and its publisher"() {
        expect:
        exchange("context", ["1"], 1) == ["1 true true"]
    }

    void "errors from a publisher invoke @OnError"() {
        expect:
        exchange("error", ["1"], 1) == ["error: boom 1"]
    }

    @Requires(property = 'spec.name', value = 'WebSocketHandlerResultSpec')
    @ServerWebSocket("/ws/result/{mode}")
    static class ResultServer {

        @OnMessage
        Publisher<?> onMessage(String mode, String message, WebSocketSession session) {
            switch (mode) {
                case "sync":
                    session.sendSync(message)
                    return Mono.empty()
                case "mono":
                    long delay = 150 - 50 * Integer.parseInt(message)
                    return Mono.delay(Duration.ofMillis(delay), Schedulers.boundedElastic())
                            .then(Mono.fromCallable { session.sendSync(message) })
                case "flux":
                    return Flux.just(message + "a", message + "b")
                            .delayElements(Duration.ofMillis(20))
                            .concatMap { m -> Mono.fromCallable { session.sendSync(m) } }
                case "context":
                    boolean inHandler = ServerRequestContext.currentRequest().isPresent()
                    return Mono.deferContextual { ctx ->
                        Mono.fromCallable {
                            session.sendSync(message + " " + inHandler + " " + ctx.hasKey(ServerRequestContext.KEY))
                        }
                    }.subscribeOn(Schedulers.boundedElastic())
                case "error":
                    return Mono.delay(Duration.ofMillis(10)).then(Mono.error(new IllegalStateException("boom " + message)))
            }
            return null
        }

        @OnError
        void onError(Throwable t, WebSocketSession session) {
            session.sendSync("error: " + t.message)
        }
    }

    @Requires(property = 'spec.name', value = 'WebSocketHandlerResultSpec')
    @ClientWebSocket
    static abstract class ResultClient implements AutoCloseable {
        final Queue<String> received = new ConcurrentLinkedQueue<>()
        WebSocketSession session

        @OnOpen
        void onOpen(WebSocketSession session) {
            this.session = session
        }

        @OnMessage
        void onMessage(String message) {
            received.add(message)
        }
    }
}
