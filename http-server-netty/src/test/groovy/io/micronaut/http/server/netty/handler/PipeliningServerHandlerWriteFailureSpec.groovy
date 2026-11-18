package io.micronaut.http.server.netty.handler

import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.body.stream.BodySizeLimits
import io.micronaut.http.body.stream.BufferConsumer
import io.micronaut.http.netty.body.NettyByteBodyFactory
import io.micronaut.http.netty.body.StreamingNettyByteBody
import io.micronaut.http.server.netty.HttpCompressionStrategy
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.EncoderException
import io.netty.handler.codec.MessageToMessageEncoder
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.DefaultHttpHeaders
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaders
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import spock.lang.Specification

import java.nio.channels.ClosedChannelException
import java.nio.charset.StandardCharsets
import java.util.function.Supplier

/**
 * A failure while an HTTP/1 response is written (its compression, a header, the discard of its
 * body) releases what the response holds, reports the response once and closes the connection.
 */
class PipeliningServerHandlerWriteFailureSpec extends Specification {

    def 'a compressed full response that fails in #stage releases its resources and is reported once'() {
        given:
        def h = harness()
        def failure = new EncoderException('compression failed')
        boolean armed = false
        def headers = new DefaultHttpHeaders() {
            @Override
            HttpHeaders set(CharSequence name, Object value) {
                if (armed && HttpHeaderNames.CONTENT_LENGTH.contentEqualsIgnoreCase(name)) {
                    throw failure
                }
                super.set(name, value)
            }
        }
        def content = bytes('full response')
        // one more reference, to detect a double release on the failure path
        content.retain()
        h.access.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, headers), h.factory.adapt(content))
        ByteBuf partial = null
        Compressor.Session session = null
        switch (stage) {
            case 'the creation of the encoder':
                session = h.replaceSession { throw failure }
                break
            case 'the encoder':
                partial = bytes('partial output')
                session = h.replaceSession { throwingEncoder(failure, partial) }
                break
            default:
                // the length of the compressed body is set once the compression has finished
                session = h.access.handler.compressionSession
                armed = true
        }

        when:
        h.resume()

        then:
        h.written == ['/']
        h.failed == [failure]
        h.errors.empty
        !h.channel.open
        h.channel.outboundMessages().empty
        content.refCnt() == 1
        partial == null || partial.refCnt() == 0
        session.@compressed == null
        session.@held == null
        session.@compressionChannel == null || !session.@compressionChannel.open

        when:
        h.access.abort()
        h.close()

        then:
        h.written == ['/']

        cleanup:
        content.release()
        h.close()

        where:
        stage << ['the creation of the encoder', 'the encoder', 'the length header after the compression']
    }

    def 'a streamed response whose compression fails releases the queued and the later signals'() {
        given:
        def h = harness()
        def failure = new EncoderException('stream write failed')
        def early = bytes('early')
        def unwritten = bytes('unwritten')
        BufferConsumer writer = h.stream(early, unwritten)
        def reentrant = bytes('reentrant')
        def partial = bytes('partial output')
        Compressor.Session session = h.replaceSession {
            throwingEncoder(failure, partial) {
                writer.add(h.factory.readBufferFactory().adapt(reentrant))
            }
        }

        when:
        h.resume()

        then:
        !h.channel.open
        !session.@compressionChannel.open
        session.@compressionChannel.outboundMessages().empty
        h.written == ['/']
        h.discards == 1
        h.failed == [failure]
        // what the writer still hands to the closed connection fails
        h.errors.every { it instanceof ClosedChannelException }
        [early, unwritten, reentrant, partial].every { it.refCnt() == 0 }

        when:
        def late = bytes('late')
        def last = bytes('last')
        writer.add(h.factory.readBufferFactory().adapt(late))
        writer.complete()
        writer.error(new IOException('late error'))
        writer.addAndComplete(h.factory.readBufferFactory().adapt(last))

        then:
        late.refCnt() == 0
        last.refCnt() == 0
        h.written == ['/']
        h.discards == 1

        cleanup:
        h.close()
    }

    def 'a failed response whose body throws from its discard is still reported and closes the connection'() {
        given:
        def h = harness()
        def failure = new IllegalStateException('discard failed')
        def pending = bytes('pending')
        BufferConsumer writer = h.stream(pending)
        h.onDiscard = {
            assert h.written == ['/']
            throw failure
        }

        when:
        h.resume()
        writer.error(new IOException('body failed'))

        then:
        def error = thrown(IllegalStateException)
        error.is(failure)
        h.written == ['/']
        h.discards == 1
        !h.channel.open
        pending.refCnt() == 0

        cleanup:
        h.close()
    }

    private Harness harness() {
        def h = new Harness()
        h.handler.setCompressionStrategy(Stub(HttpCompressionStrategy) {
            isEnabled() >> true
            shouldCompress(_) >> true
            getCompressionLevel() >> 6
            getMaxZstdEncodeSize() >> 1024 * 1024
        })
        h.request()
        h
    }

    private static MessageToMessageEncoder<ByteBuf> throwingEncoder(Throwable failure, ByteBuf partial, Closure onWrite = {}) {
        new MessageToMessageEncoder<ByteBuf>() {
            @Override
            protected void encode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) {
                onWrite()
                out.add(partial)
                throw failure
            }
        }
    }

    private static ByteBuf bytes(String value) {
        Unpooled.copiedBuffer(value, StandardCharsets.UTF_8)
    }

    private static class Harness implements RequestHandler {
        final PipeliningServerHandler handler = new PipeliningServerHandler(this)
        final EmbeddedChannel channel = new EmbeddedChannel(handler)
        final NettyByteBodyFactory factory = new NettyByteBodyFactory(channel)
        final List<Object> written = []
        final List<Throwable> failed = []
        final List<Throwable> errors = []
        PipeliningServerHandler.OutboundAccessImpl access
        int discards
        Closure onDiscard = {}

        void request() {
            // the response waits in the queue until resume()
            channel.unsafe().outboundBuffer().setUserDefinedWritability(1, false)
            def request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/')
            request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, 'gzip')
            channel.writeInbound(request)
        }

        BufferConsumer stream(ByteBuf... data) {
            def buffer = factory.createStreamingBuffer(BodySizeLimits.UNLIMITED, new BufferConsumer.Upstream() {
                @Override
                void onBytesConsumed(long bytesConsumed) {
                }

                @Override
                void allowDiscard() {
                    discards++
                    onDiscard()
                }
            })
            access.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), new StreamingNettyByteBody(buffer))
            data.each { buffer.add(factory.readBufferFactory().adapt(it)) }
            access.handler.writer
        }

        /**
         * Replace the compression session of the queued response by one that always streams
         * through the encoder of the given supplier, to inject a failure into the encoder.
         */
        Compressor.Session replaceSession(Closure<ChannelHandler> encoder) {
            def response = access.handler
            assert response.compressionSession != null
            response.compressionSession.discard()
            def constructor = Compressor.Session.getDeclaredConstructor(ChannelHandlerContext, Supplier, Compressor.OneShot)
            constructor.accessible = true
            def session = constructor.newInstance(channel.pipeline().context(handler), encoder as Supplier, null)
            response.compressionSession = session
            session
        }

        void resume() {
            try {
                channel.unsafe().outboundBuffer().setUserDefinedWritability(1, true)
                channel.runPendingTasks()
                channel.checkException()
            } catch (Throwable t) {
                errors << t
            }
        }

        @Override
        void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outbound) {
            body.close()
            access = (PipeliningServerHandler.OutboundAccessImpl) outbound
            access.attachment(request.uri())
        }

        @Override
        void responseWritten(Object attachment) {
            written << attachment
        }

        @Override
        void responseFailed(Object attachment, Throwable cause) {
            failed << cause
        }

        @Override
        void handleUnboundError(Throwable cause) {
            errors << cause
        }

        void close() {
            channel.finishAndReleaseAll()
        }
    }
}
