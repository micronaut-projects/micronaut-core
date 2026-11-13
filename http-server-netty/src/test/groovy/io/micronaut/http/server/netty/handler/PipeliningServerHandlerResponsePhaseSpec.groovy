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

class PipeliningServerHandlerResponsePhaseSpec extends Specification {
    def 'queued bytes retain their #outcome outcome until the response is activated'() {
        given:
        def h = new Harness()
        h.request('/first')
        h.request('/second')
        def second = h.writer(1)
        def early = bytes('early')
        def failure = new IOException('queued failure')

        when:
        second.add(h.readBuffer(early))
        if (outcome == 'COMPLETE') {
            second.complete()
        } else {
            second.error(failure)
        }

        then:
        second.phase.name() == 'QUEUED'
        second.pendingOutcome.name() == outcome
        second.pendingData.size() == 1
        outcome != 'FAILED' || second.pendingFailure.is(failure)
        h.written.empty
        h.channel.open

        when:
        h.writer(0).complete()

        then:
        h.channel.checkException()
        second.phase.name() == 'DONE'
        h.written == ['/first', '/second']
        h.upstreams[1].discards == (outcome == 'FAILED' ? 1 : 0)
        h.channel.open == (outcome == 'COMPLETE')
        h.messages() == (outcome == 'COMPLETE' ? ['H:/first', 'L:', 'H:/second', 'D:early', 'L:'] : ['H:/first', 'L:'])
        early.refCnt() == 0

        cleanup:
        h.close()

        where:
        outcome << ['COMPLETE', 'FAILED']
    }

    def 'headers pending handles #signal after earlier buffered bytes'() {
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
        second.add(h.readBuffer(bytes('early')))

        when:
        h.writer(0).complete()

        then:
        second.phase.name() == 'HEADERS_PENDING'
        !h.channel.writable

        when:
        switch (signal) {
            case 'add':
                h.upstreams[1].onStart = { second.add(h.readBuffer(bytes('start'))) }
                second.add(h.readBuffer(bytes('late')))
                assert second.phase.name() == 'STREAMING'
                second.complete()
                break
            case 'final buffer':
                second.addAndComplete(h.readBuffer(bytes('last')))
                break
            case 'complete':
                second.complete()
                break
            case 'error':
                second.error(new IOException('headers pending failure'))
                break
        }
        if (h.channel.open) {
            h.channel.unsafe().outboundBuffer().setUserDefinedWritability(1, true)
            h.channel.flushOutbound()
        }

        then:
        h.channel.checkException()
        second.phase.name() == 'DONE'
        h.written == ['/first', '/second']
        h.messages() == expected

        cleanup:
        h.close()

        where:
        signal         | expected
        'add'          | ['H:/first', 'L:', 'H:/second', 'D:early', 'D:late', 'D:start', 'L:']
        'final buffer' | ['H:/first', 'L:', 'H:/second', 'D:early', 'L:last']
        'complete'     | ['H:/first', 'L:', 'H:/second', 'D:early', 'L:']
        'error'        | ['H:/first', 'L:']
    }

    def 'reentrant header signals follow all bytes that were already queued'() {
        given:
        def h = new Harness()
        h.request('/first')
        h.request('/second')
        def second = h.writer(1)
        second.add(h.readBuffer(bytes('old')))
        def phases = []
        h.beforeWrite = { ChannelHandlerContext ctx, Object msg ->
            if (msg instanceof HttpResponse && msg.headers().get('x-request') == '/second') {
                phases << second.phase.name()
                second.add(h.readBuffer(bytes('new')))
                second.complete()
            }
        }

        when:
        h.writer(0).complete()
        if (h.channel.open) {
            h.channel.flushOutbound()
        }

        then:
        h.channel.checkException()
        phases == ['WRITING_HEADERS']
        second.phase.name() == 'DONE'
        h.upstreams[1].starts == 1
        h.written == ['/first', '/second']
        h.messages() == ['H:/first', 'L:', 'H:/second', 'D:old', 'D:new', 'L:']

        cleanup:
        h.close()
    }

    def 'a reentrant #signal during header writing releases pending data'() {
        given:
        def h = new Harness()
        h.request('/first')
        h.request('/second')
        h.request('/third')
        def second = h.writer(1)
        def early = bytes('early')
        second.add(h.readBuffer(early))
        def phases = []
        h.beforeWrite = { ChannelHandlerContext ctx, Object msg ->
            if (msg instanceof HttpResponse && msg.headers().get('x-request') == '/second') {
                phases << second.phase.name()
                if (signal == 'error') {
                    second.error(new IOException('reentrant header failure'))
                } else {
                    h.channel.pipeline().remove(h.handler)
                }
            }
        }

        when:
        h.writer(0).complete()
        if (h.channel.open) {
            h.channel.flushOutbound()
        }

        then:
        h.channel.checkException()
        phases == ['WRITING_HEADERS']
        second.phase.name() == 'DONE'
        h.written == ['/first', '/second', '/third']
        h.upstreams*.discards == [0, 1, 1]
        h.writer(2).phase.name() == 'DONE'
        !h.messages().any { it == 'D:early' || it == 'H:/third' }
        early.refCnt() == 0

        cleanup:
        h.close()

        where:
        signal << ['error', 'removal']
    }

    def 'completion from a read callback while headers are pending resumes the queue'() {
        given:
        def h = new Harness()
        h.channel.unsafe().outboundBuffer().setUserDefinedWritability(1, false)
        h.request('/first')
        h.request('/second')
        def first = h.writer(0)
        def phases = []
        h.beforeRead = { ChannelHandlerContext ctx ->
            phases << first.phase.name()
            first.complete()
        }

        when:
        h.channel.unsafe().outboundBuffer().setUserDefinedWritability(1, true)
        h.channel.runPendingTasks()
        h.channel.flushOutbound()

        then:
        h.channel.checkException()
        phases == ['HEADERS_PENDING']
        first.phase.name() == 'DONE'
        h.writer(1).phase.name() == 'STREAMING'
        h.written == ['/first']
        h.messages() == ['H:/first', 'L:', 'H:/second']

        cleanup:
        h.close()
    }

    def 'a refused #kind response releases its bytes without answering the accepted request'() {
        given:
        def h = new Harness()
        h.request('/first')
        def accepted = h.writer(0)
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
        refused.refCnt() == 0
        h.written.empty
        h.handler.outboundHandler.is(accepted)
        accepted.phase.name() == 'STREAMING'
        upstream.discards == (kind == 'streaming' ? 1 : 0)

        when:
        accepted.complete()

        then:
        h.channel.checkException()
        h.channel.open
        h.written == ['/first']
        h.messages() == ['H:/first', 'L:']

        cleanup:
        h.close()

        where:
        kind << ['full', 'streaming']
    }

    def 'teardown discards active and queued responses without pumping the queue'() {
        given:
        def h = new Harness()
        h.request('/first')
        h.request('/second')
        h.full = true
        h.request('/third')
        def second = h.writer(1)
        def early = bytes('early')
        second.add(h.readBuffer(early))
        def states = []
        h.upstreams.eachWithIndex { upstream, i ->
            upstream.onDiscard = {
                def writer = h.writer(i)
                states << writer.phase.name()
                writer.complete()
                writer.error(new IOException('late error'))
                writer.addAndComplete(h.readBuffer(bytes('late')))
            }
        }
        h.channel.flushOutbound()
        assert h.messages() == ['H:/first']

        when:
        h.channel.pipeline().remove(h.handler)

        then:
        h.channel.checkException()
        states == ['DONE', 'DONE']
        h.written == ['/first', '/second', '/third']
        h.accesses.every { h.responseHandler(it).phase.name() == 'DONE' }
        h.handler.outboundHandler == null
        h.upstreams*.discards == [1, 1]
        early.refCnt() == 0
        h.fullContent.refCnt() == 0
        h.messages().empty

        cleanup:
        h.close()
    }

    def 'terminal #kind writes commit done before reentrant completion and abort'() {
        given:
        def h = new Harness()
        h.full = kind == 'full'
        // Hold full responses in the queue so the reentrant observer can be installed first.
        h.channel.unsafe().outboundBuffer().setUserDefinedWritability(1, false)
        h.request('/first')
        h.full = false
        h.request('/second')
        def first = h.responseHandler(h.accesses[0])
        def phases = []
        h.beforeWrite = { ChannelHandlerContext ctx, Object msg ->
            if (msg instanceof LastHttpContent) {
                phases << first.phase.name()
                if (first instanceof BufferConsumer) {
                    first.complete()
                    first.error(new IOException('late error'))
                }
                h.accesses[0].abort()
                ctx.fireChannelWritabilityChanged()
            }
        }
        h.onWritten = { Object attachment ->
            assert h.responseHandler(h.accesses.find { it.attachment == attachment }).phase.name() == 'DONE'
            if (attachment == '/first') {
                h.accesses[0].abort()
                if (first instanceof BufferConsumer) {
                    first.complete()
                }
            }
        }

        when:
        h.channel.unsafe().outboundBuffer().setUserDefinedWritability(1, true)
        h.channel.runPendingTasks()
        if (kind == 'final buffer') {
            first.addAndComplete(h.readBuffer(bytes('last')))
        } else if (kind == 'empty') {
            first.complete()
        }
        h.channel.flushOutbound()

        then:
        h.channel.checkException()
        phases == ['DONE']
        h.written == ['/first']
        h.channel.open
        h.writer(1).phase.name() == 'STREAMING'
        h.messages() == expected

        cleanup:
        h.close()

        where:
        kind           | expected
        'full'         | ['H:/first', 'L:full', 'H:/second']
        'final buffer' | ['H:/first', 'L:last', 'H:/second']
        'empty'        | ['H:/first', 'L:', 'H:/second']
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
            if (request.headers().contains(HttpHeaderNames.EXPECT)) {
                Flux.from(body.toByteArrayPublisher()).subscribe()
            } else {
                body.close()
            }
            accesses << access
            access.attachment(request.uri())
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
