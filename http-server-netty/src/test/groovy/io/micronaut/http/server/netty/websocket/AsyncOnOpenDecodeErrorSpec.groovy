package io.micronaut.http.server.netty.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.io.buffer.ByteBuffer
import io.micronaut.core.type.Argument
import io.micronaut.core.io.buffer.ByteBufferFactory
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.codec.MediaTypeCodec
import io.micronaut.http.codec.CodecException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.WebSocketClient
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.OnError
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.micronaut.websocket.annotation.ServerWebSocket
import io.netty.channel.EventLoopGroup
import jakarta.inject.Singleton
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * A message that fails to decode while an asynchronous @OnOpen runs goes to @OnError, and reading resumes.
 */
class AsyncOnOpenDecodeErrorSpec extends Specification {

    void "a decoding failure for a message received during an async OnOpen reaches OnError and reading resumes"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'AsyncOnOpenDecodeErrorSpec'])
        DecodeEndpoint.RECEIVED.clear()
        DecodeEndpoint.ERRORS.clear()
        WebSocketClient wsClient = server.applicationContext.createBean(WebSocketClient, server.URI)
        TextClientWebSocket ws = Mono.from(wsClient.connect(TextClientWebSocket, '/async-open/decode')).block(Duration.ofSeconds(10))

        when: 'a message the codec rejects arrives while the open handler runs'
        ws.sendText('bad').get(10, TimeUnit.SECONDS)

        then:
        new PollingConditions(timeout: 10).eventually {
            DecodeEndpoint.ERRORS.size() == 1
        }

        when:
        ws.sendText('good').get(10, TimeUnit.SECONDS)

        then:
        new PollingConditions(timeout: 10).eventually {
            DecodeEndpoint.RECEIVED == ['good']
        }

        cleanup:
        ws?.close()
        wsClient?.close()
        server?.close()
    }

    static class DecodeBody {
        final String value

        DecodeBody(String value) {
            this.value = value
        }
    }

    @Requires(property = 'spec.name', value = 'AsyncOnOpenDecodeErrorSpec')
    @Singleton
    static class DecodeCodec implements MediaTypeCodec {
        @Override
        Collection<MediaType> getMediaTypes() {
            return [MediaType.of('application/x-async-open-decode')]
        }

        @Override
        <T> T decode(Argument<T> type, ByteBuffer<?> buffer) throws CodecException {
            String value = buffer.toString(StandardCharsets.UTF_8)
            if (value == 'bad') {
                // not a CodecException, so that it is not handled as a message processing error
                throw new IllegalStateException('malformed')
            }
            return (T) new DecodeBody(value)
        }

        @Override
        <T> T decode(Argument<T> type, InputStream inputStream) throws CodecException {
            throw new UnsupportedOperationException()
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
        <T, B> ByteBuffer<B> encode(T object, ByteBufferFactory<?, B> allocator) throws CodecException {
            throw new UnsupportedOperationException()
        }
    }

    @Requires(property = 'spec.name', value = 'AsyncOnOpenDecodeErrorSpec')
    @ServerWebSocket("/async-open/decode")
    static class DecodeEndpoint {
        static final List<String> RECEIVED = new CopyOnWriteArrayList<>()
        static final List<Throwable> ERRORS = new CopyOnWriteArrayList<>()
        private final EventLoopGroup eventLoopGroup

        DecodeEndpoint(EventLoopGroup eventLoopGroup) {
            this.eventLoopGroup = eventLoopGroup
        }

        @OnOpen
        CompletableFuture<Void> onOpen(WebSocketSession session) {
            CompletableFuture<Void> result = new CompletableFuture<>()
            eventLoopGroup.next().schedule({ result.complete(null) }, 1, TimeUnit.SECONDS)
            return result
        }

        @OnMessage
        @Consumes('application/x-async-open-decode')
        void onMessage(DecodeBody message) {
            RECEIVED.add(message.value)
        }

        @OnError
        void onError(Throwable error) {
            ERRORS.add(error)
        }
    }
}
