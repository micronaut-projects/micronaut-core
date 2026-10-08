package io.micronaut.http.server.netty.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.AsyncWebSocketClient
import io.micronaut.websocket.WebSocketClient
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.ClientWebSocket
import io.micronaut.websocket.annotation.OnError
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.micronaut.websocket.annotation.ServerWebSocket
import jakarta.inject.Singleton
import reactor.core.publisher.Flux
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.time.Duration
import java.util.concurrent.BlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * The stages the handlers of a client endpoint return: awaited by the AsyncWebSocketClient of the
 * Netty client, values for the reactive WebSocketClient, as before.
 */
class AsyncWebSocketClientStagesSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec.name': 'AsyncWebSocketClientStagesSpec'])

    @Shared
    @AutoCleanup
    WebSocketClient client = embeddedServer.applicationContext.createBean(WebSocketClient, embeddedServer.getURI())

    PollingConditions conditions = new PollingConditions(timeout: 10)

    void 'the async client completes the connect once the stage of the open method completed'() {
        given:
        OpenStages stages = embeddedServer.applicationContext.getBean(OpenStages)
        AsyncWebSocketClient async = client.toAsyncWebSocket()

        when:
        CompletableFuture<PendingOpenClient> connect = async.connect(PendingOpenClient, '/stages').toCompletableFuture()
        CompletableFuture<Object> opened = stages.opened.poll(10, TimeUnit.SECONDS)

        then:
        opened != null
        !connect.isDone()

        when:
        opened.complete(null)

        then:
        connect.get(10, TimeUnit.SECONDS) != null

        cleanup:
        connect?.getNow(null)?.close()
    }

    void 'the reactive client does not wait for the stage of the open method, as before'() {
        given:
        OpenStages stages = embeddedServer.applicationContext.getBean(OpenStages)

        when:
        PendingOpenClient endpoint = Flux.from(client.connect(PendingOpenClient, '/stages')).blockFirst(Duration.ofSeconds(10))
        CompletableFuture<Object> opened = stages.opened.poll(10, TimeUnit.SECONDS)

        then:
        endpoint != null
        !opened.isDone()

        cleanup:
        opened?.complete(null)
        endpoint?.close()
    }

    void 'with the async client a failed stage of a message handler reaches the error handler'() {
        given:
        AsyncWebSocketClient async = client.toAsyncWebSocket()
        FailingStageClient endpoint = async.connect(FailingStageClient, '/stages').toCompletableFuture().get(10, TimeUnit.SECONDS)

        when:
        endpoint.send('fail')

        then:
        conditions.eventually {
            endpoint.errors.any { it.message == 'stage failed' }
        }

        cleanup:
        endpoint?.close()
    }

    void 'with the reactive client a failed stage of a message handler is a value, as before'() {
        given:
        FailingStageClient endpoint = Flux.from(client.connect(FailingStageClient, '/stages')).blockFirst(Duration.ofSeconds(10))

        when:
        endpoint.send('fail')
        endpoint.send('after')

        then:
        conditions.eventually {
            endpoint.handled.contains('after')
        }
        endpoint.errors.empty

        cleanup:
        endpoint?.close()
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'AsyncWebSocketClientStagesSpec')
    static class OpenStages {
        final BlockingQueue<CompletableFuture<Object>> opened = new LinkedBlockingQueue<>()
    }

    @Requires(property = 'spec.name', value = 'AsyncWebSocketClientStagesSpec')
    @ServerWebSocket('/stages')
    static class EchoServer {
        @OnMessage
        CompletionStage<?> onMessage(String message, WebSocketSession session) {
            return session.sendAsync(message)
        }
    }

    @Requires(property = 'spec.name', value = 'AsyncWebSocketClientStagesSpec')
    @ClientWebSocket('/stages')
    static abstract class PendingOpenClient implements AutoCloseable {
        @jakarta.inject.Inject
        OpenStages stages

        @OnOpen
        CompletionStage<?> onOpen() {
            CompletableFuture<Object> opened = new CompletableFuture<>()
            stages.opened.add(opened)
            return opened
        }

        @OnMessage
        void onMessage(String message) {
        }
    }

    @Requires(property = 'spec.name', value = 'AsyncWebSocketClientStagesSpec')
    @ClientWebSocket('/stages')
    static abstract class FailingStageClient implements AutoCloseable {
        final List<String> handled = new CopyOnWriteArrayList<>()
        final Collection<Throwable> errors = new ConcurrentLinkedQueue<>()

        @OnMessage
        CompletionStage<?> onMessage(String message) {
            if (message == 'fail') {
                return CompletableFuture.failedFuture(new IllegalStateException('stage failed'))
            }
            handled.add(message)
            return CompletableFuture.completedFuture(null)
        }

        @OnError
        void onError(Throwable error) {
            errors.add(error)
        }

        abstract void send(String message)
    }
}
