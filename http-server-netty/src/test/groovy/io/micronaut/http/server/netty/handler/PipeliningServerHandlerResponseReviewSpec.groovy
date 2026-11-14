package io.micronaut.http.server.netty.handler

import io.micronaut.buffer.netty.NettyReadBufferFactory
import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.body.stream.BodySizeLimits
import io.micronaut.http.body.stream.BufferConsumer
import io.micronaut.http.netty.body.NettyByteBodyFactory
import io.micronaut.http.netty.body.StreamingNettyByteBody
import io.micronaut.http.server.netty.HttpCompressionStrategy
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
import java.util.zip.GZIPInputStream

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

    def 'compression resources are discarded on #signal without sending a follower'() {
        given:
        def h = new Harness()
        enableCompression(h)
        h.gzip = true
        h.request('/first')
        def first = h.writer(0)
        def sent = bytes('sent')
        first.add(h.readBuffer(sent))
        // Observe the actual encoder's resource lifetime, not response phase fields.
        EmbeddedChannel encoder = first.compressionSession.compressionChannel()
        h.request('/second')
        def pending = bytes('pending')
        h.writer(1).add(h.readBuffer(pending))
        h.upstreams.eachWithIndex { upstream, i ->
            upstream.onDiscard = { assert h.written.contains(i == 0 ? '/first' : '/second') }
        }
        h.channel.flushOutbound()
        assert h.messages().first() == 'H:/first'

        when:
        if (signal == 'error') {
            first.error(new IOException('body failed'))
        } else {
            h.channel.pipeline().remove(h.handler)
        }

        then:
        h.channel.checkException()
        !encoder.open
        encoder.outboundMessages().empty
        h.written == ['/first', '/second']
        h.upstreams*.discards == [1, 1]
        sent.refCnt() == 0
        pending.refCnt() == 0
        h.messages().empty

        cleanup:
        h.close()

        where:
        signal << ['error', 'removal']
    }

    def 'a compressed full terminal write is complete before reentrant abort'() {
        given:
        def h = new Harness()
        enableCompression(h)
        h.gzip = true
        h.full = true
        h.channel.unsafe().outboundBuffer().setUserDefinedWritability(1, false)
        h.request('/full')
        h.beforeWrite = { ChannelHandlerContext ctx, Object msg ->
            h.accesses[0].abort()
            ctx.fireChannelWritabilityChanged()
        }

        when:
        h.channel.unsafe().outboundBuffer().setUserDefinedWritability(1, true)
        h.channel.flushOutbound()
        HttpResponse headers = h.channel.readOutbound()
        LastHttpContent last = h.channel.readOutbound()
        byte[] compressed = new byte[last.content().readableBytes()]
        last.content().readBytes(compressed)

        then:
        h.channel.checkException()
        headers.headers().get(HttpHeaderNames.CONTENT_ENCODING) == 'gzip'
        headers.headers().getInt(HttpHeaderNames.CONTENT_LENGTH) == compressed.length
        new GZIPInputStream(new ByteArrayInputStream(compressed)).text == 'full'
        h.written == ['/full']
        h.channel.open
        h.channel.readOutbound() == null
        h.fullContent.refCnt() == 0

        cleanup:
        last?.release()
        h.close()
    }

    def 'refusing a #kind response does not prepare another compression session'() {
        given:
        def h = new Harness()
        int preparations = 0
        h.handler.setCompressionStrategy(Stub(HttpCompressionStrategy) {
            isEnabled() >> true
            shouldCompress(_) >> { preparations++; true }
            getCompressionLevel() >> 6
            getMaxZstdEncodeSize() >> 1024 * 1024
        })
        h.gzip = true
        h.request('/first')
        h.channel.flushOutbound()
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
        preparations == 1
        refused.refCnt() == 0
        h.written.empty
        h.channel.open

        cleanup:
        h.close()

        where:
        kind << ['full', 'streaming']
    }

    def 'removal #point releases a late response and notifies its attachment once'() {
        given:
        def h = new Harness()
        def statuses = []
        h.beforeWrite = { ChannelHandlerContext ctx, Object msg ->
            if (msg instanceof HttpResponse) {
                statuses << msg.status().code()
                if (msg.status() == HttpResponseStatus.CONTINUE && removeInWrite) {
                    ctx.channel().pipeline().remove(h.handler)
                }
            }
        }
        if (precedingResponse) {
            h.request('/first')
        }
        h.autoRespond = autoRespond
        h.request('/late', expectContinue)
        if (!removeInWrite) {
            h.channel.flushOutbound()
            h.channel.pipeline().remove(h.handler)
        }
        ByteBuf late = null
        if (!autoRespond) {
            late = bytes('late')
            h.accesses.last().write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK),
                new NettyByteBodyFactory(h.channel).adapt(late))
        }
        def duplicate = bytes('duplicate')

        when:
        h.accesses.last().write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK),
            new NettyByteBodyFactory(h.channel).adapt(duplicate))

        then:
        thrown IllegalStateException
        h.channel.checkException()
        h.written == (precedingResponse ? ['/first', '/late'] : ['/late'])
        statuses == (precedingResponse ? [200] : expectContinue ? [100] : [])
        late == null || late.refCnt() == 0
        duplicate.refCnt() == 0
        !h.messages().any { it == 'H:/late' || it == 'L:' }

        cleanup:
        h.close()

        where:
        point                                | expectContinue | precedingResponse | autoRespond | removeInWrite
        'without continue'                   | false          | false             | false       | false
        'with continue active and no next'   | true           | false             | false       | false
        'during continue'                    | true           | false             | true        | true
        'with continue queued and no next'   | true           | true              | false       | false
        'with continue and its next queued'  | true           | true              | true        | false
    }

    def 'a streaming error closes the connection and discards its pipelined follower'() {
        given:
        def h = new Harness()
        h.request('/first')
        h.request('/second')
        def first = h.writer(0)
        first.add(h.readBuffer(bytes('sent')))
        def pending = bytes('pending')
        h.writer(1).add(h.readBuffer(pending))
        h.channel.flushOutbound()
        assert h.messages() == ['H:/first', 'D:sent']

        when:
        first.error(new IOException('stream failed'))

        then:
        h.channel.checkException()
        !h.channel.open
        h.written == ['/first', '/second']
        h.upstreams*.discards == [1, 1]
        pending.refCnt() == 0
        h.messages().empty

        cleanup:
        h.close()
    }

    def 'a queued failure wins over completion in #order order'() {
        given:
        def h = new Harness()
        h.request('/first')
        h.request('/second')
        def second = h.writer(1)
        def pending = bytes('pending')
        second.add(h.readBuffer(pending))

        when:
        if (order == 'complete then failed') {
            second.complete()
            second.error(new IOException('queued failure'))
        } else {
            second.error(new IOException('queued failure'))
            second.complete()
        }

        then:
        h.written.empty
        h.channel.open

        when:
        h.writer(0).complete()

        then:
        h.channel.checkException()
        !h.channel.open
        h.written == ['/first', '/second']
        h.upstreams*.discards == [0, 1]
        pending.refCnt() == 0
        h.messages() == ['H:/first', 'L:']

        cleanup:
        h.close()

        where:
        order << ['complete then failed', 'failed then complete']
    }

    def 'early bytes are acknowledged once and only when writable with continue #expectContinue'() {
        given:
        def h = new Harness()
        if (expectContinue) {
            h.beforeWrite = { ChannelHandlerContext ctx, Object msg ->
                if (msg instanceof HttpResponse && msg.status() == HttpResponseStatus.CONTINUE) {
                    ctx.channel().unsafe().outboundBuffer().setUserDefinedWritability(1, false)
                }
            }
        }
        h.request('/first')
        h.request('/second', expectContinue)
        def second = h.writer(1)
        second.add(h.readBuffer(bytes('early')))
        if (!expectContinue) {
            h.channel.unsafe().outboundBuffer().setUserDefinedWritability(1, false)
        }
        h.writer(0).complete()

        when:
        second.add(h.readBuffer(bytes('late')))
        h.channel.pipeline().fireChannelWritabilityChanged()

        then:
        !h.channel.writable
        h.upstreams[1].consumed.empty

        when:
        h.channel.unsafe().outboundBuffer().setUserDefinedWritability(1, true)
        2.times { h.channel.pipeline().fireChannelWritabilityChanged() }

        then:
        h.upstreams[1].consumed == [9L]
        h.upstreams[1].starts == 1

        when:
        h.channel.unsafe().outboundBuffer().setUserDefinedWritability(1, false)
        second.add(h.readBuffer(bytes('end')))

        then:
        h.upstreams[1].consumed == [9L]

        when:
        h.channel.unsafe().outboundBuffer().setUserDefinedWritability(1, true)
        h.channel.pipeline().fireChannelWritabilityChanged()
        second.complete()
        h.channel.flushOutbound()

        then:
        h.channel.checkException()
        h.upstreams[1].consumed == [9L, 3L]
        h.messages() == ['H:/first', 'L:', 'H:/second', 'D:early', 'D:late', 'D:end', 'L:']

        cleanup:
        h.close()

        where:
        expectContinue << [false, true]
    }

    private void enableCompression(Harness h) {
        h.handler.setCompressionStrategy(Stub(HttpCompressionStrategy) {
            isEnabled() >> true
            shouldCompress(_) >> true
            getCompressionLevel() >> 6
            getMaxZstdEncodeSize() >> 1024 * 1024
        })
    }

    def 'marking close in #stage for full #full preserves committed headers and closes at the end'() {
        given:
        def h = new Harness()
        String connection = null
        h.beforeWrite = { ChannelHandlerContext ctx, Object msg ->
            if (msg instanceof HttpResponse && msg.status() == HttpResponseStatus.CONTINUE) {
                ctx.channel().unsafe().outboundBuffer().setUserDefinedWritability(1, false)
            }
            if (msg instanceof HttpResponse && msg.headers().get('x-request') == '/target') {
                if (stage == 'WRITING_HEADERS') {
                    h.accesses.last().closeAfterWriteInEventLoop()
                }
                connection = msg.headers().get(HttpHeaderNames.CONNECTION)
            }
        }
        if (stage in ['QUEUED', 'HEADERS_PENDING']) {
            h.request('/blocker')
        }
        h.full = full
        h.request('/target', stage == 'HEADERS_PENDING')
        def target = h.writer(h.accesses.size() - 1)
        if (stage == 'HEADERS_PENDING') {
            h.writer(0).complete()
        }
        if (stage == 'DONE' && !full) {
            target.complete()
        }

        when:
        if (stage != 'WRITING_HEADERS') {
            h.accesses.last().closeAfterWriteInEventLoop()
        }
        if (stage == 'QUEUED') {
            h.writer(0).complete()
        } else if (stage == 'HEADERS_PENDING') {
            h.channel.unsafe().outboundBuffer().setUserDefinedWritability(1, true)
        }
        if (!full && stage != 'DONE') {
            target.complete()
        }
        if (h.channel.open) {
            h.channel.flushOutbound()
        }

        then:
        h.channel.checkException()
        connection == (stage in ['QUEUED', 'HEADERS_PENDING'] ? 'close' : null)
        h.channel.open == (stage == 'DONE')
        h.written == (stage in ['QUEUED', 'HEADERS_PENDING'] ? ['/blocker', '/target'] : ['/target'])
        h.messages() == (stage in ['QUEUED', 'HEADERS_PENDING'] ? ['H:/blocker', 'L:'] : []) + ['H:/target', full ? 'L:full' : 'L:']

        cleanup:
        h.close()

        where:
        stage             | full
        'QUEUED'          | false
        'QUEUED'          | true
        'HEADERS_PENDING' | false
        'HEADERS_PENDING' | true
        'WRITING_HEADERS' | false
        'STREAMING'       | false
        'DONE'            | false
        'DONE'            | true
    }

    def 'late closeAfterWrite closes after last content and discards a follower'() {
        given:
        def h = new Harness()
        String connection = null
        h.beforeWrite = { ChannelHandlerContext ctx, Object msg ->
            if (msg instanceof HttpResponse && msg.headers().get('x-request') == '/first') {
                connection = msg.headers().get(HttpHeaderNames.CONNECTION)
            }
        }
        h.request('/first')
        h.request('/second')

        when:
        h.accesses[0].closeAfterWrite()
        h.writer(0).addAndComplete(h.readBuffer(bytes('last')))

        then:
        h.channel.checkException()
        connection == null
        !h.channel.open
        h.written.toSorted() == ['/first', '/second']
        h.messages() == ['H:/first', 'L:last']
        h.upstreams[1].discards == 1

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
        boolean full
        boolean autoRespond = true
        boolean gzip
        ByteBuf fullContent

        Harness() {
            channel = new EmbeddedChannel(new ChannelOutboundHandlerAdapter() {
                @Override
                void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                    beforeWrite(ctx, msg)
                    ctx.write(msg, promise)
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
            if (gzip) {
                request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, 'gzip')
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
        final List<Long> consumed = []
        Closure onDiscard = { }

        @Override
        void start() {
            starts++
        }

        @Override
        void onBytesConsumed(long bytesConsumed) {
            consumed << bytesConsumed
        }

        @Override
        void allowDiscard() {
            discards++
            onDiscard()
        }
    }
}
