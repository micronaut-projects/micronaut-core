package io.micronaut.http.server.netty.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.WebSocketClient
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.ClientWebSocket
import io.micronaut.websocket.annotation.OnError
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.ServerWebSocket
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList

class ClientHandlerFragmentsSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec.name': 'ClientHandlerFragmentsSpec'])

    @Shared
    @AutoCleanup
    WebSocketClient client = embeddedServer.applicationContext.createBean(WebSocketClient, embeddedServer.getURI())

    PollingConditions conditions = new PollingConditions(timeout: 10)

    void "a client handler that fails later while the next message is fragmented does not truncate it"() {
        given:
        FragClient c = Flux.from(client.connect(FragClient, "/client-stage-fragments")).blockFirst()

        when:
        c.send('go')
        then:
        conditions.eventually { FragClient.pending != null }

        when: 'the first fragment of the next message arrives, then the stage of the previous one fails'
        Thread.sleep(300)
        FragClient.pending.completeExceptionally(new IllegalStateException('late failure'))
        conditions.eventually { !c.errors.isEmpty() }
        c.send('finish')

        then:
        conditions.eventually { !c.handled.isEmpty() }
        c.handled == ['hello']

        cleanup:
        c?.close()
    }

    @Requires(property = 'spec.name', value = 'ClientHandlerFragmentsSpec')
    @ServerWebSocket('/client-stage-fragments')
    static class FragServer {
        @OnMessage
        CompletionStage<?> onMessage(String message, WebSocketSession session) {
            if (message == 'go') {
                return session.sendAsync('first').thenCompose { session.sendAsync(new TextWebSocketFrame(false, 0, 'hel')) }
            }
            return session.sendAsync(new ContinuationWebSocketFrame(true, 0, 'lo'))
        }
    }

    @Requires(property = 'spec.name', value = 'ClientHandlerFragmentsSpec')
    @ClientWebSocket('/client-stage-fragments')
    static abstract class FragClient implements AutoCloseable {
        static volatile CompletableFuture<Object> pending
        final List<String> handled = new CopyOnWriteArrayList<>()
        final Collection<Throwable> errors = new ConcurrentLinkedQueue<>()

        @OnMessage
        Mono<?> onMessage(String message) {
            if (message == 'first') {
                pending = new CompletableFuture<>()
                return Mono.fromFuture(pending)
            }
            handled.add(message)
            return Mono.empty()
        }

        @OnError
        void onError(Throwable error) {
            errors.add(error)
        }

        abstract void send(String message)
    }
}
