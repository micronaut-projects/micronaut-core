package io.micronaut.http.server.netty.websocket

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.io.buffer.ByteBuffer
import io.micronaut.core.type.Argument
import io.micronaut.core.type.Headers
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.body.MessageBodyReader
import io.micronaut.http.codec.CodecException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.OnError
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.OnOpen
import io.micronaut.websocket.annotation.ServerWebSocket
import io.netty.buffer.ByteBuf
import io.netty.channel.EventLoopGroup
import jakarta.inject.Singleton
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.net.http.HttpClient
import java.net.http.WebSocket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * A message that fails to decode while an asynchronous @OnOpen runs goes to @OnError, and reading resumes.
 */
class AsyncOnOpenDecodeErrorSpec extends Specification {

    void "a reader failure for a message received during an async OnOpen reaches OnError and reading resumes"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'AsyncOnOpenDecodeErrorSpec'])
        DecodeEndpoint.RECEIVED.clear()
        DecodeEndpoint.ERRORS.clear()
        HttpClient httpClient = HttpClient.newHttpClient()
        WebSocket ws = httpClient.newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:${server.port}/async-open/decode"), new WebSocket.Listener() {})
                .get(10, TimeUnit.SECONDS)

        when: 'a message the reader rejects arrives while the open handler runs'
        ws.sendText('bad', true).get(10, TimeUnit.SECONDS)

        then:
        new PollingConditions(timeout: 10).eventually {
            DecodeEndpoint.ERRORS.size() == 1
        }

        when:
        ws.sendText('good', true).get(10, TimeUnit.SECONDS)

        then:
        new PollingConditions(timeout: 10).eventually {
            DecodeEndpoint.RECEIVED == ['good']
        }

        cleanup:
        ws?.abort()
        if (httpClient instanceof AutoCloseable) { // the JDK HTTP client can only be closed since Java 21
            httpClient.close()
        }
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
    @Consumes('application/x-async-open-decode')
    static class DecodeReader implements MessageBodyReader<DecodeBody> {
        @Override
        DecodeBody read(Argument<DecodeBody> type, MediaType mediaType, Headers httpHeaders, ByteBuffer<?> byteBuffer) throws CodecException {
            String value = ((ByteBuf) byteBuffer.asNativeBuffer()).toString(StandardCharsets.UTF_8)
            if (value == 'bad') {
                throw new CodecException('malformed')
            }
            ((ByteBuf) byteBuffer.asNativeBuffer()).release()
            return new DecodeBody(value)
        }

        @Override
        DecodeBody read(Argument<DecodeBody> type, MediaType mediaType, Headers httpHeaders, InputStream inputStream) throws CodecException {
            throw new CodecException('malformed')
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
