package io.micronaut.http.server.netty.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.io.buffer.ByteBufferFactory
import io.micronaut.core.type.Argument
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.codec.CodecException
import io.micronaut.http.codec.MediaTypeCodec
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.annotation.OnError
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.ServerWebSocket
import jakarta.inject.Singleton
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
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
        server?.close()
    }

    private WebSocket connect(String path, WebSocket.Listener listener = new WebSocket.Listener() {}) {
        httpClient.newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:${server.port}$path"), listener)
                .get(10, TimeUnit.SECONDS)
    }

    void "a decoding failure for a queued message goes to @OnError and reading resumes"() {
        given:
        DecodeServer bean = server.applicationContext.getBean(DecodeServer)
        ws = connect('/queued-decode')

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
        bean.errors[0] instanceof IllegalStateException
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

    static class Body {
        final String value

        Body(String value) {
            this.value = value
        }
    }

    @Requires(property = 'spec.name', value = 'WebSocketQueuedMessageSpec')
    @Singleton
    static class BodyCodec implements MediaTypeCodec {
        @Override
        Collection<MediaType> getMediaTypes() {
            return [MediaType.of('application/x-queued')]
        }

        @Override
        <T> T decode(Argument<T> type, InputStream inputStream) throws CodecException {
            String value = new String(inputStream.bytes, StandardCharsets.UTF_8)
            if (value == 'boom') {
                throw new IllegalStateException('boom')
            }
            return (T) new Body(value)
        }

        @Override
        <T> void encode(T object, OutputStream outputStream) throws CodecException {
            throw new UnsupportedOperationException()
        }

        @Override
        <T> byte[] encode(T object) throws CodecException {
            throw new UnsupportedOperationException()
        }

        @Override
        <T, B> io.micronaut.core.io.buffer.ByteBuffer<B> encode(T object, ByteBufferFactory<?, B> allocator) throws CodecException {
            throw new UnsupportedOperationException()
        }
    }

    @Requires(property = 'spec.name', value = 'WebSocketQueuedMessageSpec')
    @ServerWebSocket('/queued-decode')
    static class DecodeServer {
        final CountDownLatch latch = new CountDownLatch(1)
        final List<String> received = new CopyOnWriteArrayList<>()
        final List<Throwable> errors = new CopyOnWriteArrayList<>()

        @OnMessage
        @Consumes('application/x-queued')
        Mono<Boolean> onMessage(Body body) {
            received.add(body.value)
            if (body.value != 'block') {
                return Mono.just(true)
            }
            return Mono.fromCallable { latch.await(30, TimeUnit.SECONDS) }.subscribeOn(Schedulers.boundedElastic())
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
        Mono<Boolean> onMessage(String message) {
            return Mono.fromCallable { latch.await(30, TimeUnit.SECONDS) }.subscribeOn(Schedulers.boundedElastic())
        }
    }

    @Requires(property = 'spec.name', value = 'WebSocketQueuedMessageSpec')
    @ServerWebSocket('/queued-recover')
    static class RecoveringServer {
        final CountDownLatch latch = new CountDownLatch(1)
        final List<String> received = new CopyOnWriteArrayList<>()
        final List<Throwable> errors = new CopyOnWriteArrayList<>()

        @OnMessage
        Mono<Boolean> onMessage(String message) {
            if (message == 'fail') {
                return Mono.<Boolean>fromCallable {
                    latch.await(30, TimeUnit.SECONDS)
                    throw new IllegalStateException('fail')
                }.subscribeOn(Schedulers.boundedElastic())
            }
            received.add(message)
            return Mono.just(true)
        }

        @OnError
        void onError(Throwable t) {
            errors.add(t)
        }
    }
}
