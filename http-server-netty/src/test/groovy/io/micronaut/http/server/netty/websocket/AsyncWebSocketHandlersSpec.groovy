package io.micronaut.http.server.netty.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.WebSocketBroadcaster
import io.micronaut.websocket.WebSocketClient
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.ClientWebSocket
import io.micronaut.websocket.annotation.OnError
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.micronaut.websocket.annotation.ServerWebSocket
import io.micronaut.websocket.exceptions.WebSocketSessionException
import reactor.core.publisher.Flux
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

class AsyncWebSocketHandlersSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec.name': 'AsyncWebSocketHandlersSpec'])

    @Shared
    @AutoCleanup
    WebSocketClient client = embeddedServer.applicationContext.createBean(WebSocketClient, embeddedServer.getURI())

    PollingConditions conditions = new PollingConditions(timeout: 10)

    void "the reactive client treats the stage of a handler as a value, as before"() {
        given:
        StageClient stageClient = connect('failing')

        when:
        stageClient.send('fail')
        stageClient.send('after')

        then: 'the failure of the stage does not reach the error handler'
        conditions.eventually {
            stageClient.handled.contains('after')
        }
        stageClient.errors.empty
        stageClient.session.open

        cleanup:
        stageClient?.close()
    }

    void "the reactive connect does not wait for the stage of the open method, as before"() {
        when:
        PendingOpenClient pendingOpen = Flux.from(client.connect(PendingOpenClient, "/async-handlers/pending-open")).blockFirst(Duration.ofSeconds(10))

        then:
        pendingOpen != null
        !pendingOpen.opened.isDone()

        cleanup:
        pendingOpen?.opened?.complete(null)
        pendingOpen?.close()
    }

    void "a client handler is done once its stage completes"() {
        given:
        StageClient stageClient = connect('completing')

        when:
        stageClient.send('first')
        stageClient.send('second')

        then:
        conditions.eventually {
            // the messages of a client are handled as they are read: the stages complete in any order
            stageClient.handled.sort(false) == ['first', 'second']
        }
        stageClient.errors.empty

        cleanup:
        stageClient?.close()
    }

    void "broadcastAsync completes with the message once it is written to the matching sessions"() {
        given:
        StageClient inRoom = connect('broadcast')
        StageClient elsewhere = connect('other')
        WebSocketBroadcaster broadcaster = embeddedServer.applicationContext.getBean(WebSocketBroadcaster)

        when:
        String sent = broadcaster.broadcastAsync('to the room', { WebSocketSession s -> s.getUriVariables().get('room', String).orElse(null) == 'broadcast' })
            .get(10, TimeUnit.SECONDS)

        then:
        sent == 'to the room'
        conditions.eventually {
            inRoom.handled.contains('to the room')
        }
        !elsewhere.handled.contains('to the room')

        cleanup:
        inRoom?.close()
        elsewhere?.close()
    }

    void "broadcast sends for each subscription only"() {
        given:
        StageClient inRoom = connect('lazy')
        WebSocketBroadcaster broadcaster = embeddedServer.applicationContext.getBean(WebSocketBroadcaster)
        def toRoom = { WebSocketSession s -> s.getUriVariables().get('room', String).orElse(null) == 'lazy' }

        when:
        def publisher = broadcaster.broadcast('not subscribed', toRoom)
        def emitted = Flux.from(broadcaster.broadcast('subscribed', toRoom)).collectList().block()

        then:
        publisher != null
        emitted == ['subscribed']
        conditions.eventually {
            inRoom.handled.contains('subscribed')
        }
        !inRoom.handled.contains('not subscribed')

        cleanup:
        inRoom?.close()
    }

    void "send on a closed session fails the publisher, not the call"() {
        given:
        StageClient stageClient = connect('closed')
        WebSocketSession session = stageClient.session
        session.close()
        conditions.eventually {
            !session.open
        }

        when:
        def publisher = session.send('too late')

        then:
        publisher != null

        when:
        Flux.from(publisher).blockLast()

        then:
        thrown(WebSocketSessionException)

        cleanup:
        stageClient?.close()
    }

    void "the reactive send writes once subscribed, and a cancel after that does not take the message back, as before"() {
        given:
        StageClient stageClient = connect('cancel-send')

        when:
        def unsubscribed = stageClient.session.send('unsubscribed')
        Flux.from(stageClient.session.send('cancelled')).subscribe().dispose()
        Flux.from(stageClient.session.send('last')).blockLast()

        then: 'the echo server replies to the messages that were sent'
        unsubscribed != null
        conditions.eventually {
            stageClient.handled.containsAll(['cancelled', 'last'])
        }
        !stageClient.handled.contains('unsubscribed')

        cleanup:
        stageClient?.close()
    }

    private StageClient connect(String room) {
        return Flux.from(client.connect(StageClient, "/async-handlers/$room")).blockFirst()
    }

    @Requires(property = 'spec.name', value = 'AsyncWebSocketHandlersSpec')
    @ServerWebSocket('/async-handlers/{room}')
    static class EchoServer {
        @OnMessage
        CompletionStage<?> onMessage(String message, WebSocketSession session) {
            return session.sendAsync(message)
        }
    }

    @Requires(property = 'spec.name', value = 'AsyncWebSocketHandlersSpec')
    @ClientWebSocket('/async-handlers/{room}')
    static abstract class StageClient implements AutoCloseable {
        final List<String> handled = new CopyOnWriteArrayList<>()
        final Collection<Throwable> errors = new ConcurrentLinkedQueue<>()
        WebSocketSession session

        @OnOpen
        void onOpen(WebSocketSession session) {
            this.session = session
        }

        @OnMessage
        CompletionStage<?> onMessage(String message) {
            if (message == 'fail') {
                return CompletableFuture.failedFuture(new IllegalStateException('handler stage failed'))
            }
            // completes later: the handler is done only then
            return CompletableFuture.supplyAsync({ handled.add(message) }, CompletableFuture.delayedExecutor(50, TimeUnit.MILLISECONDS))
        }

        @OnError
        void onError(Throwable error) {
            errors.add(error)
        }

        abstract void send(String message)
    }

    @Requires(property = 'spec.name', value = 'AsyncWebSocketHandlersSpec')
    @ClientWebSocket('/async-handlers/{room}')
    static abstract class PendingOpenClient implements AutoCloseable {
        final CompletableFuture<Object> opened = new CompletableFuture<>()

        @OnOpen
        CompletionStage<?> onOpen() {
            return opened
        }

        @OnMessage
        void onMessage(String message) {
        }
    }
}
