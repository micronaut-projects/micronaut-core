package io.micronaut.http.server.netty.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.core.io.buffer.ByteBuffer
import io.micronaut.core.type.Argument
import io.micronaut.core.type.Headers
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.body.MessageBodyReader
import io.micronaut.http.codec.CodecException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.websocket.annotation.OnError
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.ServerWebSocket
import io.micronaut.websocket.event.WebSocketMessageProcessedEvent
import io.netty.buffer.ByteBuf
import jakarta.inject.Singleton
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.net.http.HttpClient
import java.net.http.WebSocket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Messages read while the handler of a previous message is running are queued.
 */
class WebSocketQueuedMessageSpec extends Specification {

    EmbeddedServer server
    HttpClient httpClient
    WebSocket ws

    def setup() {
        server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'WebSocketQueuedMessageSpec'])
        httpClient = HttpClient.newHttpClient()
    }

    def cleanup() {
        ws?.abort()
        httpClient?.close()
        server?.close()
    }

    private WebSocket connect(String path, WebSocket.Listener listener = new WebSocket.Listener() {}) {
        httpClient.newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:${server.port}$path"), listener)
                .get(10, TimeUnit.SECONDS)
    }

    void "a body reader failure for a queued message goes to @OnError and reading resumes"() {
        given:
        ReaderServer bean = server.applicationContext.getBean(ReaderServer)
        ws = connect('/queued-reader')

        when:
        ws.sendText('block', true).get(10, TimeUnit.SECONDS)
        ws.sendText('boom', true).get(10, TimeUnit.SECONDS)
        ws.sendText('ok', true).get(10, TimeUnit.SECONDS)
        sleep(300)
        bean.latch.countDown()

        then:
        new PollingConditions(timeout: 10).eventually {
            assert bean.errors.size() == 1
            assert bean.received == ['block', 'ok']
        }
        bean.errors[0] instanceof CodecException
    }

    void "pings and a close are answered while a message handler is running"() {
        given:
        PingServer bean = server.applicationContext.getBean(PingServer)
        CompletableFuture<String> pong = new CompletableFuture<>()
        CompletableFuture<Integer> closed = new CompletableFuture<>()
        ws = connect('/queued-ping', new WebSocket.Listener() {
            @Override
            CompletionStage<?> onPong(WebSocket webSocket, java.nio.ByteBuffer message) {
                pong.complete(StandardCharsets.UTF_8.decode(message).toString())
                webSocket.request(1)
                return null
            }

            @Override
            CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                closed.complete(statusCode)
                return null
            }
        })

        when:
        ws.sendText('block', true).get(10, TimeUnit.SECONDS)
        sleep(300)
        ws.sendPing(java.nio.ByteBuffer.wrap('hb'.bytes)).get(10, TimeUnit.SECONDS)

        then:
        pong.get(5, TimeUnit.SECONDS) == 'hb'

        when:
        ws.sendClose(WebSocket.NORMAL_CLOSURE, 'bye').get(10, TimeUnit.SECONDS)

        then:
        closed.get(5, TimeUnit.SECONDS) == WebSocket.NORMAL_CLOSURE

        cleanup:
        bean.latch.countDown()
    }

    void "messages queued behind a failed message are handled when @OnError recovers"() {
        given:
        RecoveringServer bean = server.applicationContext.getBean(RecoveringServer)
        ws = connect('/queued-recover')

        when:
        ws.sendText('fail', true).get(10, TimeUnit.SECONDS)
        ws.sendText('a', true).get(10, TimeUnit.SECONDS)
        ws.sendText('b', true).get(10, TimeUnit.SECONDS)
        sleep(300)
        bean.latch.countDown()

        then:
        new PollingConditions(timeout: 10).eventually {
            assert bean.errors.size() == 1
            assert bean.received == ['a', 'b']
        }
    }

    void "a buffer handed over by a body reader is readable in the processed event"() {
        given:
        ProcessedListener listener = server.applicationContext.getBean(ProcessedListener)
        ws = connect('/queued-event')

        when:
        ws.sendBinary(java.nio.ByteBuffer.wrap(new byte[100]), false).get(10, TimeUnit.SECONDS)
        ws.sendBinary(java.nio.ByteBuffer.wrap(new byte[100]), true).get(10, TimeUnit.SECONDS)

        then:
        new PollingConditions(timeout: 10).eventually {
            assert listener.readable.size() == 1
        }
        // the payload is still readable, not released
        listener.readable[0] instanceof Integer
    }

    static class Body {
        final String value

        Body(String value) {
            this.value = value
        }
    }

    @Requires(property = 'spec.name', value = 'WebSocketQueuedMessageSpec')
    @Singleton
    @Consumes('application/x-queued')
    static class BodyReader implements MessageBodyReader<Body> {
        @Override
        Body read(Argument<Body> type, MediaType mediaType, Headers httpHeaders, ByteBuffer<?> byteBuffer) throws CodecException {
            String value = byteBuffer.toString(StandardCharsets.UTF_8)
            if (value == 'boom') {
                throw new CodecException('boom')
            }
            ((ByteBuf) byteBuffer.asNativeBuffer()).release()
            return new Body(value)
        }

        @Override
        Body read(Argument<Body> type, MediaType mediaType, Headers httpHeaders, InputStream inputStream) throws CodecException {
            throw new UnsupportedOperationException()
        }
    }

    @Requires(property = 'spec.name', value = 'WebSocketQueuedMessageSpec')
    @ServerWebSocket('/queued-reader')
    static class ReaderServer {
        final CountDownLatch latch = new CountDownLatch(1)
        final List<String> received = new CopyOnWriteArrayList<>()
        final List<Throwable> errors = new CopyOnWriteArrayList<>()

        @OnMessage
        @Consumes('application/x-queued')
        @ExecuteOn(TaskExecutors.BLOCKING)
        void onMessage(Body body) {
            received.add(body.value)
            if (body.value == 'block') {
                latch.await(30, TimeUnit.SECONDS)
            }
        }

        @OnError
        void onError(Throwable t) {
            errors.add(t)
        }
    }

    @Requires(property = 'spec.name', value = 'WebSocketQueuedMessageSpec')
    @ServerWebSocket('/queued-ping')
    static class PingServer {
        final CountDownLatch latch = new CountDownLatch(1)

        @OnMessage
        @ExecuteOn(TaskExecutors.BLOCKING)
        void onMessage(String message) {
            latch.await(30, TimeUnit.SECONDS)
        }
    }

    @Requires(property = 'spec.name', value = 'WebSocketQueuedMessageSpec')
    @ServerWebSocket('/queued-recover')
    static class RecoveringServer {
        final CountDownLatch latch = new CountDownLatch(1)
        final List<String> received = new CopyOnWriteArrayList<>()
        final List<Throwable> errors = new CopyOnWriteArrayList<>()

        @OnMessage
        @ExecuteOn(TaskExecutors.BLOCKING)
        void onMessage(String message) {
            if (message == 'fail') {
                latch.await(30, TimeUnit.SECONDS)
                throw new IllegalStateException('fail')
            }
            received.add(message)
        }

        @OnError
        void onError(Throwable t) {
            errors.add(t)
        }
    }

    @Requires(property = 'spec.name', value = 'WebSocketQueuedMessageSpec')
    @ServerWebSocket('/queued-event')
    static class EventServer {
        @OnMessage
        @Consumes(MediaType.APPLICATION_OCTET_STREAM)
        void onMessage(ByteBuf body) {
        }
    }

    @Requires(property = 'spec.name', value = 'WebSocketQueuedMessageSpec')
    @Singleton
    static class ProcessedListener implements ApplicationEventListener<WebSocketMessageProcessedEvent<?>> {
        final List<Object> readable = new CopyOnWriteArrayList<>()

        @Override
        void onApplicationEvent(WebSocketMessageProcessedEvent<?> event) {
            if (event.message instanceof ByteBuf) {
                ByteBuf buf = (ByteBuf) event.message
                try {
                    buf.getByte(0)
                    readable.add(buf.readableBytes())
                } catch (Exception e) {
                    readable.add(e)
                }
            }
        }
    }
}
