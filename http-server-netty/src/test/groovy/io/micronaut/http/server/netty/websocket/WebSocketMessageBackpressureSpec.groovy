package io.micronaut.http.server.netty.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.ServerWebSocket
import spock.lang.Specification

import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * A @ServerWebSocket bean handles the messages of a connection one at a time.
 */
class WebSocketMessageBackpressureSpec extends Specification {

    void "a bean websocket bounds concurrently dispatched messages per connection"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'WebSocketMessageBackpressureSpec'])
        HttpClient httpClient = HttpClient.newHttpClient()
        BlockingServer bean = server.applicationContext.getBean(BlockingServer)
        WebSocket ws = httpClient.newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:${server.port}/message-backpressure"), new WebSocket.Listener() {})
                .get(10, TimeUnit.SECONDS)

        when:
        100.times {
            ws.sendText('x' * 100, true).get(10, TimeUnit.SECONDS)
        }
        sleep(3000)

        then: "the server does not run every sent message concurrently"
        bean.maxConcurrent.get() < 100

        cleanup:
        bean?.latch?.countDown()
        ws?.abort()
        if (httpClient instanceof AutoCloseable) { // the JDK HTTP client can only be closed since Java 21
            httpClient.close()
        }
        server?.close()
    }

    @Requires(property = 'spec.name', value = 'WebSocketMessageBackpressureSpec')
    @ServerWebSocket("/message-backpressure")
    static class BlockingServer {
        final CountDownLatch latch = new CountDownLatch(1)
        final AtomicInteger concurrent = new AtomicInteger()
        final AtomicInteger maxConcurrent = new AtomicInteger()

        @OnMessage
        @ExecuteOn(TaskExecutors.BLOCKING)
        void onMessage(String message) {
            int c = concurrent.incrementAndGet()
            maxConcurrent.accumulateAndGet(c, Math::max)
            try {
                latch.await(30, TimeUnit.SECONDS)
            } finally {
                concurrent.decrementAndGet()
            }
        }
    }
}
