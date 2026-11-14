package io.micronaut.http.server.netty.handler

import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.body.stream.BodySizeLimits
import io.micronaut.http.body.stream.BufferConsumer
import io.micronaut.http.netty.body.NettyByteBodyFactory
import io.micronaut.http.netty.body.StreamingNettyByteBody
import io.micronaut.http.server.netty.HttpCompressionStrategy
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
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

import java.nio.charset.StandardCharsets
import java.nio.channels.ClosedChannelException
import java.util.function.Supplier

class PipeliningServerHandlerWriteFailureSpec extends Specification {
    def 'a compressed full response failing during #stage releases resources and answers once'() {
        given:
        def h = harness()
        def failure = new EncoderException('terminal write failed')
        boolean armed = false
        def headers = new DefaultHttpHeaders() {
            @Override
            HttpHeaders set(CharSequence name, Object value) {
                if (armed && name == HttpHeaderNames.CONTENT_LENGTH) {
                    throw failure
                }
                super.set(name, value)
            }
        }
        def content = bytes('full response')
        // Keep one caller-owned reference to detect a double release on the failure path.
        content.retain()
        h.access.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, headers), h.factory.adapt(content))
        // Private access is only used to inject the failure into the real compression pipeline.
        EmbeddedChannel encoder = h.access.handler.compressionSession.compressionChannel()
        ByteBuf partial = null
        if (stage == 'encoding') {
            partial = bytes('partial output')
            encoder.pipeline().addLast(throwingEncoder(failure, partial))
        } else {
            armed = true
        }

        when:
        h.resume()

        then:
        h.written == ['/']
        !h.channel.open
        !encoder.open
        encoder.outboundMessages().empty
        h.channel.outboundMessages().empty
        h.errors.size() == 1
        h.errors[0].is(failure)
        content.refCnt() == 1
        partial == null || partial.refCnt() == 0

        when:
        h.access.abort()
        h.close()

        then:
        h.written == ['/']

        cleanup:
        content.release()
        h.close()

        where:
        stage << ['encoding', 'content length after compression finished']
    }

    def 'an encoder creation failure consumes the full response and answers once'() {
        given:
        def h = harness()
        def failure = new EncoderException('encoder creation failed')
        def content = bytes('full response')
        h.access.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), h.factory.adapt(content))
        h.access.handler.compressionSession = new Compressor.Session(h.channel.pipeline().context(h.handler), { throw failure } as Supplier)

        when:
        h.resume()

        then:
        h.written == ['/']
        !h.channel.open
        h.channel.outboundMessages().empty
        h.errors == [failure]
        content.refCnt() == 0

        cleanup:
        h.close()
    }

    def 'a streaming #stage failure releases queued and later signals without hanging'() {
        given:
        def h = harness()
        def failure = new EncoderException('stream write failed')
        def early = bytes('early')
        def unwritten = bytes('unwritten')
        BufferConsumer writer = h.stream(early, unwritten)
        def reentrant = bytes('reentrant')
        def partial = bytes('partial output')
        EmbeddedChannel encoder = h.access.handler.compressionSession.compressionChannel()
        if (stage == 'content') {
            encoder.pipeline().addLast(throwingEncoder(failure, partial) {
                writer.add(h.factory.readBufferFactory().adapt(reentrant))
            })
        } else {
            partial.release()
            def ctx = h.channel.pipeline().context(h.handler)
            // Netty converts outbound-handler exceptions to failed promises. Inject a
            // synchronous context failure to exercise the header-writing stack itself.
            h.handler.ctx = Stub(ChannelHandlerContext) {
                executor() >> { ctx.executor() }
                channel() >> { ctx.channel() }
                voidPromise() >> { ctx.voidPromise() }
                newPromise() >> { ctx.newPromise() }
                read() >> { ctx.read() }
                close() >> { ctx.close() }
                write(_, _) >> {
                    writer.add(h.factory.readBufferFactory().adapt(reentrant))
                    throw failure
                }
            }
        }

        when:
        h.resume()

        then:
        !h.channel.open
        !encoder.open
        h.written == ['/']
        h.discards == 1
        h.errors.count { it.is(failure) } == 1
        h.errors.every { it.is(failure) || it instanceof ClosedChannelException }
        [early, unwritten, reentrant, partial].every { it.refCnt() == 0 }
        encoder.outboundMessages().empty
        h.channel.outboundMessages().empty

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

        where:
        stage << ['header', 'content']
    }

    def 'a throwing discard still notifies before allowing discard and closes the channel'() {
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
        final List<Throwable> errors = []
        OutboundAccess access
        int discards
        Closure onDiscard = {}

        void request() {
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
            access.handler as BufferConsumer
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
            access = outbound
            access.attachment(request.uri())
        }

        @Override
        void responseWritten(Object attachment) {
            written << attachment
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
