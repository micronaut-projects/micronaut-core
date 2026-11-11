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
import io.micronaut.websocket.CloseReason
import io.micronaut.websocket.WebSocketClient
import io.micronaut.websocket.annotation.OnClose
import io.micronaut.websocket.annotation.OnError
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.ServerWebSocket
import jakarta.inject.Singleton
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Messages read while the handler of a previous message is running are queued.
 */
class WebSocketQueuedMessageSpec extends Specification {

    EmbeddedServer server
    WebSocketClient wsClient
    TextClientWebSocket ws

    def setup() {
        server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'WebSocketQueuedMessageSpec'])
        wsClient = server.applicationContext.createBean(WebSocketClient, server.URI)
    }

    def cleanup() {
        ws?.close()
        wsClient?.close()
        server?.close()
    }

    private TextClientWebSocket connect(String path) {
        Mono.from(wsClient.connect(TextClientWebSocket, path)).block(Duration.ofSeconds(10))
    }

    void "a decoding failure for a queued message goes to @OnError and reading resumes"() {
        given:
        DecodeServer bean = server.applicationContext.getBean(DecodeServer)
        ws = connect('/queued-decode')

        when:
        ws.sendText('block').get(10, TimeUnit.SECONDS)
        ws.sendText('boom').get(10, TimeUnit.SECONDS)
        ws.sendText('ok').get(10, TimeUnit.SECONDS)
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
        ws = connect('/queued-ping')

        when:
        ws.sendText('block').get(10, TimeUnit.SECONDS)
        sleep(300)
        ws.sendPing('hb').get(10, TimeUnit.SECONDS)

        then:
        ws.pong.get(5, TimeUnit.SECONDS) == 'hb'

        when:
        ws.close(new CloseReason(CloseReason.NORMAL.code, 'bye'))

        then: 'the server handles the close frame while the message handler is still blocked'
        bean.latch.count == 1
        bean.closed.get(5, TimeUnit.SECONDS).code == CloseReason.NORMAL.code

        cleanup:
        bean.latch.countDown()
    }

    void "messages queued behind a failed message are handled when @OnError recovers"() {
        given:
        RecoveringServer bean = server.applicationContext.getBean(RecoveringServer)
        ws = connect('/queued-recover')

        when:
        ws.sendText('fail').get(10, TimeUnit.SECONDS)
        ws.sendText('a').get(10, TimeUnit.SECONDS)
        ws.sendText('b').get(10, TimeUnit.SECONDS)
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
        final CompletableFuture<CloseReason> closed = new CompletableFuture<>()

        @OnClose
        void onClose(CloseReason reason) {
            closed.complete(reason)
        }

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
