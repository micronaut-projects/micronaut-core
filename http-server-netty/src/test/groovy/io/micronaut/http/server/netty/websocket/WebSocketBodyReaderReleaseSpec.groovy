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
import io.micronaut.websocket.WebSocketClient
import io.micronaut.websocket.WebSocketSession
import io.micronaut.websocket.annotation.ClientWebSocket
import io.micronaut.websocket.annotation.OnMessage
import io.micronaut.websocket.annotation.ServerWebSocket
import io.netty.buffer.ByteBuf
import jakarta.inject.Singleton
import reactor.core.publisher.Flux
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.concurrent.CopyOnWriteArrayList

class WebSocketBodyReaderReleaseSpec extends Specification {

    def 'the frame is released when the body reader fails'() {
        given:
        def ctx = ApplicationContext.run(['spec.name': 'WebSocketBodyReaderReleaseSpec'])
        def server = ctx.getBean(EmbeddedServer).start()
        def reader = ctx.getBean(FailingReader)
        def client = ctx.createBean(WebSocketClient, server.URL)

        when:
        def socket = Flux.from(client.connect(ReleaseClient, '/reader-release')).blockFirst()
        socket.session.sendSync('malformed')

        then:
        new PollingConditions(timeout: 5).eventually {
            reader.seen.size() == 1
            // only the reference the reader holds is left
            reader.seen[0].refCnt() == 1
        }

        cleanup:
        reader?.seen?.each { it.release() }
        socket?.close()
        client.close()
        server.close()
        ctx.close()
    }

    static class ReleaseBody {
    }

    @Requires(property = 'spec.name', value = 'WebSocketBodyReaderReleaseSpec')
    @Singleton
    @Consumes('application/x-reader-release')
    static class FailingReader implements MessageBodyReader<ReleaseBody> {
        final List<ByteBuf> seen = new CopyOnWriteArrayList<>()

        @Override
        ReleaseBody read(Argument<ReleaseBody> type, MediaType mediaType, Headers httpHeaders, ByteBuffer<?> byteBuffer) throws CodecException {
            // hold a reference, so that the pooled buffer is not recycled before the test checks it
            seen.add(((ByteBuf) byteBuffer.asNativeBuffer()).retain())
            throw new CodecException('malformed')
        }

        @Override
        ReleaseBody read(Argument<ReleaseBody> type, MediaType mediaType, Headers httpHeaders, InputStream inputStream) throws CodecException {
            throw new CodecException('malformed')
        }
    }

    @Requires(property = 'spec.name', value = 'WebSocketBodyReaderReleaseSpec')
    @ServerWebSocket('/reader-release')
    static class ReleaseServer {
        @OnMessage
        @Consumes('application/x-reader-release')
        void onMessage(ReleaseBody body) {
            // never called: the body reader fails on every message, and the spec checks that the failed read releases the buffer
        }
    }

    @Requires(property = 'spec.name', value = 'WebSocketBodyReaderReleaseSpec')
    @ClientWebSocket('/reader-release')
    static abstract class ReleaseClient implements AutoCloseable {
        WebSocketSession session

        @io.micronaut.websocket.annotation.OnOpen
        void onOpen(WebSocketSession session) {
            this.session = session
        }

        @OnMessage
        void onMessage(String message) {
        }
    }
}
