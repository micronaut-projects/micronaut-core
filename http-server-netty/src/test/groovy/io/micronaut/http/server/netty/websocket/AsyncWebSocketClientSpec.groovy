package io.micronaut.http.server.netty.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.AsyncWebSocketClient
import io.micronaut.websocket.WebSocketClient
import io.micronaut.websocket.exceptions.WebSocketClientException
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

class AsyncWebSocketClientSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec.name': 'SimpleTextWebSocketSpec'])

    void "connect and exchange messages with #client"() {
        given:
        AsyncWebSocketClient async = asyncClient(client)
        PollingConditions conditions = new PollingConditions(timeout: 10)

        when:
        ChatClientWebSocket fred = async.connect(ChatClientWebSocket, "/chat/async-${client.replace(" ", "-")}/fred").toCompletableFuture().get(10, TimeUnit.SECONDS)
        ChatClientWebSocket bob = async.connect(ChatClientWebSocket, [topic: "async-${client.replace(" ", "-")}".toString(), username: 'bob']).toCompletableFuture().get(10, TimeUnit.SECONDS)

        then:
        fred.session.open
        bob.session.open
        fred.username == 'fred'
        bob.username == 'bob'
        conditions.eventually {
            fred.replies.contains("[bob] Joined!")
        }

        when:
        fred.send("Hello bob!")

        then:
        conditions.eventually {
            bob.replies.contains("[fred] Hello bob!")
        }

        cleanup:
        fred?.close()
        bob?.close()
        closeUnlessInjected(client, async)

        where:
        client << ['netty', 'reactive adapter', 'injected']
    }

    void "a failed connect completes the stage exceptionally with #client"() {
        given:
        AsyncWebSocketClient async = asyncClient(client)

        when:
        async.connect(ChatClientWebSocket, 'ws://localhost:1/chat/topic/user').toCompletableFuture().get(10, TimeUnit.SECONDS)

        then:
        ExecutionException e = thrown()
        e.cause instanceof WebSocketClientException

        cleanup:
        closeUnlessInjected(client, async)

        where:
        client << ['netty', 'reactive adapter', 'injected']
    }

    void "cancelling the connect cancels the stage with #client"() {
        given:
        AsyncWebSocketClient async = asyncClient(client)

        when:
        CompletableFuture<ChatClientWebSocket> future = async.connect(ChatClientWebSocket, "/chat/cancelled/fred").toCompletableFuture()
        boolean cancelled = future.cancel(true)

        then:
        !cancelled || future.isCancelled()
        // the client still connects afterwards
        async.connect(ChatClientWebSocket, "/chat/after-cancel/fred").toCompletableFuture().get(10, TimeUnit.SECONDS).session.open

        cleanup:
        async.close()

        where:
        client << ['netty', 'reactive adapter']
    }

    void "the injected client is the bean of the default client"() {
        expect:
        embeddedServer.applicationContext.getBean(AsyncWebSocketClient) != null
        embeddedServer.applicationContext.getBean(InjectedClients).client != null
    }

    private static void closeUnlessInjected(String client, AsyncWebSocketClient async) {
        if (client != 'injected') {
            async.close()
        }
    }

    private AsyncWebSocketClient asyncClient(String client) {
        if (client == 'injected') {
            return embeddedServer.applicationContext.getBean(InjectedClients).client
        }
        WebSocketClient wsClient = embeddedServer.applicationContext.createBean(WebSocketClient, embeddedServer.getURI())
        if (client == 'netty') {
            return wsClient.toAsyncWebSocket()
        }
        // only the reactive connect: the default implementation of toAsyncWebSocket adapts it
        WebSocketClient reactiveOnly = new WebSocketClient() {
            @Override
            def <T extends AutoCloseable> Publisher<T> connect(Class<T> clientEndpointType, MutableHttpRequest<?> request) {
                return wsClient.connect(clientEndpointType, request)
            }

            @Override
            def <T extends AutoCloseable> Publisher<T> connect(Class<T> clientEndpointType, Map<String, Object> parameters) {
                return wsClient.connect(clientEndpointType, parameters)
            }

            @Override
            void close() {
                wsClient.close()
            }
        }
        return reactiveOnly.toAsyncWebSocket()
    }

    @Requires(property = 'spec.name', value = 'SimpleTextWebSocketSpec')
    @Singleton
    static class InjectedClients {
        @Inject
        @Client('/')
        AsyncWebSocketClient client
    }
}
