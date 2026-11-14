package io.micronaut.http.server.netty.handler

import io.micronaut.buffer.netty.NettyReadBufferFactory
import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.body.stream.BodySizeLimits
import io.micronaut.http.body.stream.BufferConsumer
import io.micronaut.http.netty.body.NettyByteBodyFactory
import io.micronaut.http.netty.body.StreamingNettyByteBody
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.DefaultHttpRequest
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.HttpContent
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.LastHttpContent
import io.netty.util.ReferenceCountUtil
import reactor.core.publisher.Flux
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class PipeliningServerHandlerResponseReviewSpec extends Specification {
    def 'an outer #signal commits pending headers before reentrant bytes in submission order'() {
        given:
        def h = new Harness()
        h.beforeWrite = { ChannelHandlerContext ctx, Object msg ->
            if (msg instanceof HttpResponse && msg.status() == HttpResponseStatus.CONTINUE) {
                ctx.channel().unsafe().outboundBuffer().setUserDefinedWritability(1, false)
            }
        }
        h.request('/first')
        h.request('/second', true)
        def second = h.writer(1)
        def x = bytes('X')
        def y = bytes('Y')
        h.writer(0).complete()
        assert !h.channel.writable
        h.beforeWrite = { ChannelHandlerContext ctx, Object msg ->
            if (msg instanceof HttpResponse && msg.headers().get('x-request') == '/second') {
                second.add(h.readBuffer(y))
            }
        }

        when:
        if (signal == 'add') {
            second.add(h.readBuffer(x))
            second.complete()
        } else {
            second.addAndComplete(h.readBuffer(x))
        }
        h.channel.unsafe().outboundBuffer().setUserDefinedWritability(1, true)
        h.channel.flushOutbound()

        then:
        h.channel.checkException()
        h.messages() == ['H:/first', 'L:', 'H:/second', 'D:X', 'D:Y', 'L:']
        h.written == ['/first', '/second']
        x.refCnt() == 0
        y.refCnt() == 0

        cleanup:
        h.close()

        where:
        signal << ['add', 'addAndComplete']
    }

    def 'continue is emitted once while the final response is still pending'() {
        given:
        def h = new Harness()
        h.autoRespond = false
        def statuses = []
        h.beforeWrite = { ChannelHandlerContext ctx, Object msg ->
            if (msg instanceof HttpResponse) {
                statuses << msg.status().code()
            }
        }
        h.request('/continue', true)

        when:
        2.times { h.channel.pipeline().fireChannelWritabilityChanged() }
        def response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
        response.headers().set('x-request', '/continue')
        h.accesses[0].write(response, NettyByteBodyFactory.empty())
        h.channel.pipeline().fireChannelWritabilityChanged()

        then:
        h.channel.checkException()
        statuses == [100, 200]
        h.messages() == ['H:/continue', 'L:']
        h.written == ['/continue']
        h.channel.open

        cleanup:
        h.close()
    }

    def 'a second #kind write after removal with continue active releases bytes and answers once'() {
        given:
        def h = new Harness()
        h.beforeWrite = { ChannelHandlerContext ctx, Object msg ->
            if (msg instanceof HttpResponse && msg.status() == HttpResponseStatus.CONTINUE) {
                ctx.channel().unsafe().outboundBuffer().setUserDefinedWritability(1, false)
            }
        }
        h.request('/continue', true)
        h.channel.pipeline().remove(h.handler)
        def refused = bytes('refused')
        def factory = new NettyByteBodyFactory(h.channel)
        def upstream = new RecordingUpstream()
        def body
        if (kind == 'streaming') {
            def buffer = factory.createStreamingBuffer(BodySizeLimits.UNLIMITED, upstream)
            buffer.add(h.readBuffer(refused))
            body = new StreamingNettyByteBody(buffer)
        } else {
            body = factory.adapt(refused)
        }

        when:
        h.accesses[0].write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), body)

        then:
        thrown IllegalStateException
        h.written == ['/continue']
        refused.refCnt() == 0
        upstream.discards == (kind == 'streaming' ? 1 : 0)
        h.channel.flushOutbound()
        h.messages().empty

        cleanup:
        h.close()

        where:
        kind << ['full', 'streaming']
    }

    def 'a completed writer cannot be activated again'() {
        given:
        def h = new Harness()
        h.request('/first')
        def writer = h.writer(0)
        writer.complete()

        when:
        writer.activate()

        then:
        thrown IllegalStateException
        h.channel.open
        h.written == ['/first']
        h.messages() == ['H:/first', 'L:']

        when:
        h.channel.pipeline().fireChannelWritabilityChanged()
        writer.complete()
        h.accesses[0].abort()

        then:
        h.channel.checkException()
        h.channel.open
        h.written == ['/first']
        h.messages().empty

        cleanup:
        h.close()
    }

    private static ByteBuf bytes(String value) {
        Unpooled.copiedBuffer(value, StandardCharsets.UTF_8)
    }

    private static class Harness implements RequestHandler {
        final PipeliningServerHandler handler = new PipeliningServerHandler(this)
        final EmbeddedChannel channel
        final List<OutboundAccess> accesses = []
        final List<RecordingUpstream> upstreams = []
        final List<Object> written = []
        Closure beforeWrite = { ChannelHandlerContext ctx, Object msg -> }
        Closure beforeRead = { ChannelHandlerContext ctx -> }
        Closure onWritten = { Object attachment -> }
        boolean full
        boolean autoRespond = true
        ByteBuf fullContent

        Harness() {
            channel = new EmbeddedChannel(new ChannelOutboundHandlerAdapter() {
                @Override
                void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                    beforeWrite(ctx, msg)
                    ctx.write(msg, promise)
                }

                @Override
                void read(ChannelHandlerContext ctx) {
                    beforeRead(ctx)
                    ctx.read()
                }
            }, handler)
        }

        void request(String uri, boolean expectContinue = false) {
            def request = expectContinue ? new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, uri) :
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri)
            if (expectContinue) {
                request.headers().set(HttpHeaderNames.EXPECT, HttpHeaderValues.CONTINUE)
                request.headers().set(HttpHeaderNames.CONTENT_LENGTH, 1)
            }
            channel.writeInbound(request)
        }

        @Override
        void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess access) {
            accesses << access
            access.attachment(request.uri())
            if (request.headers().contains(HttpHeaderNames.EXPECT)) {
                Flux.from(body.toByteArrayPublisher()).subscribe({}, {})
            } else {
                body.close()
            }
            if (!autoRespond) {
                return
            }
            def response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
            response.headers().set('x-request', request.uri())
            def factory = new NettyByteBodyFactory(channel)
            if (full) {
                fullContent = bytes('full')
                access.write(response, factory.adapt(fullContent))
            } else {
                def upstream = new RecordingUpstream()
                upstreams << upstream
                def buffer = factory.createStreamingBuffer(BodySizeLimits.UNLIMITED, upstream)
                access.write(response, new StreamingNettyByteBody(buffer))
            }
        }

        static def responseHandler(OutboundAccess access) {
            def current = access.handler
            current instanceof PipeliningServerHandler.ContinueOutboundHandler ? current.next : current
        }

        def writer(int index) {
            responseHandler(accesses[index])
        }

        def readBuffer(ByteBuf buf) {
            NettyReadBufferFactory.of(channel.alloc()).adapt(buf)
        }

        @Override
        void responseWritten(Object attachment) {
            written << attachment
            onWritten(attachment)
        }

        @Override
        void handleUnboundError(Throwable cause) {
            throw new AssertionError(cause)
        }

        List<String> messages() {
            List<String> result = []
            Object msg
            while ((msg = channel.readOutbound()) != null) {
                try {
                    if (msg instanceof HttpResponse && msg.status() != HttpResponseStatus.CONTINUE) {
                        result << 'H:' + msg.headers().get('x-request')
                    }
                    if (msg instanceof HttpContent && !(msg instanceof HttpResponse && msg.status() == HttpResponseStatus.CONTINUE)) {
                        result << (msg instanceof LastHttpContent ? 'L:' : 'D:') + msg.content().toString(StandardCharsets.UTF_8)
                    }
                } finally {
                    ReferenceCountUtil.release(msg)
                }
            }
            result
        }

        void close() {
            channel.finishAndReleaseAll()
        }
    }

    private static class RecordingUpstream implements BufferConsumer.Upstream {
        int starts
        int discards
        Closure onStart = { }
        Closure onDiscard = { }

        @Override
        void start() {
            starts++
            onStart()
        }

        @Override
        void onBytesConsumed(long bytesConsumed) {
        }

        @Override
        void allowDiscard() {
            discards++
            onDiscard()
        }
    }
}
