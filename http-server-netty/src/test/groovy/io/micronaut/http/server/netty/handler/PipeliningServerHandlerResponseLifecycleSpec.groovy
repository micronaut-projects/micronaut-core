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

/**
 * The signals of a response body, a close marked for the connection, a refused second response,
 * a failure and the removal of the handler, at each point of the life of an HTTP/1 response:
 * queued behind an earlier one, current but not written yet, being written, and done. Each
 * response is reported written once, and every buffer that is not written is released.
 */
class PipeliningServerHandlerResponseLifecycleSpec extends Specification {
    private static final String QUEUED = 'Queued'
    private static final String WRITING = 'Writing'
    private static final String FINISHING = 'Finishing'
    private static final String WRITTEN = 'Written'

    def 'a queued response keeps its bytes and its #outcome until the response before it is written'() {
        given:
        def h = new Harness()
        h.request('/first')
        h.request('/second')
        def second = h.writer(1)
        def early = bytes('early')
        def failure = new IOException('queued failure')

        when:
        second.add(h.readBuffer(early))
        if (outcome == 'completion') {
            second.complete()
        } else {
            second.error(failure)
        }

        then:
        h.state(1) == QUEUED
        early.refCnt() == 1
        h.written.empty
        h.channel.open

        when:
        h.writer(0).complete()

        then:
        h.messages() == (outcome == 'completion' ? ['H:/first', 'E:', 'H:/second', 'E:early'] : ['H:/first', 'E:'])
        h.channel.checkException()
        h.state(1) == WRITTEN
        h.written == ['/first', '/second']
        h.failed == (outcome == 'completion' ? [] : [failure])
        h.upstreams[1].discards == (outcome == 'completion' ? 0 : 1)
        h.channel.open == (outcome == 'completion')
        early.refCnt() == 0

        cleanup:
        h.close()

        where:
        outcome << ['completion', 'failure']
    }

    def 'a queued failure wins over a completion in #order order'() {
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
        h.messages() == ['H:/first', 'E:']
        h.channel.checkException()
        !h.channel.open
        h.written == ['/first', '/second']
        h.upstreams*.discards == [0, 1]
        pending.refCnt() == 0

        cleanup:
        h.close()

        where:
        order << ['complete then failed', 'failed then complete']
    }

    def 'a response that is current but not written yet handles #signal after earlier bytes'() {
        given:
        def h = new Harness()
        h.beforeWrite = { ChannelHandlerContext ctx, Object msg ->
            if (msg instanceof HttpResponse && msg.status() == HttpResponseStatus.CONTINUE) {
                // the 100 Continue fills the connection: the response is not written yet
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
        h.state(1) == WRITING
        !h.channel.writable

        when:
        switch (signal) {
            case 'add':
                h.upstreams[1].onStart = { second.add(h.readBuffer(bytes('start'))) }
                second.add(h.readBuffer(bytes('late')))
                second.complete()
                break
            case 'final buffer':
                second.addAndComplete(h.readBuffer(bytes('last')))
                break
            case 'complete':
                second.complete()
                break
            case 'error':
                second.error(new IOException('failure before the response is written'))
                break
        }
        if (h.channel.open) {
            h.channel.unsafe().outboundBuffer().setUserDefinedWritability(1, true)
        }

        then:
        h.messages() == expected
        h.channel.checkException()
        h.state(1) == WRITTEN
        h.written == ['/first', '/second']
        h.statuses == (signal == 'error' ? [200, 100] : [200, 100, 200])

        cleanup:
        h.close()

        where:
        signal         | expected
        'add'          | ['H:/first', 'E:', 'H:/second', 'E:earlylatestart']
        'final buffer' | ['H:/first', 'E:', 'H:/second', 'E:earlylast']
        'complete'     | ['H:/first', 'E:', 'H:/second', 'E:early']
        'error'        | ['H:/first', 'E:']
    }

    def 'a #signal from inside the write of the response head releases the bytes that are not written'() {
        given:
        def h = new Harness()
        h.request('/first')
        h.request('/second')
        h.request('/third')
        def second = h.writer(1)
        def early = bytes('early')
        second.add(h.readBuffer(early))
        def states = []
        h.beforeWrite = { ChannelHandlerContext ctx, Object msg ->
            if (msg instanceof HttpResponse && msg.headers().get('x-request') == '/second') {
                states << h.state(1)
                if (signal == 'error') {
                    second.error(new IOException('failure inside the write of the head'))
                } else {
                    h.channel.pipeline().remove(h.handler)
                }
            }
        }

        when:
        h.writer(0).complete()
        def messages = h.messages()

        then:
        h.channel.checkException()
        states == [WRITING]
        h.state(1) == WRITTEN
        h.state(2) == WRITTEN
        h.written == ['/first', '/second', '/third']
        h.upstreams*.discards == [0, 1, 1]
        !messages.contains('H:/third')
        !messages.any { it.contains('early') }
        early.refCnt() == 0

        cleanup:
        h.close()

        where:
        signal << ['error', 'removal']
    }

    def 'bytes and a completion signalled from inside the write of the response head are written or released, and reported once'() {
        given:
        def h = new Harness()
        h.request('/first')
        h.request('/second')
        def second = h.writer(1)
        def old = bytes('old')
        def added = bytes('new')
        second.add(h.readBuffer(old))
        h.beforeWrite = { ChannelHandlerContext ctx, Object msg ->
            if (msg instanceof HttpResponse && msg.headers().get('x-request') == '/second') {
                second.add(h.readBuffer(added))
                second.complete()
            }
        }

        when:
        h.writer(0).complete()
        def messages = h.messages()

        then:
        h.channel.checkException()
        // a pipeline handler that signals the body from inside the write of the head is not
        // supported: the order of what reaches the connection is not pinned here
        messages.take(2) == ['H:/first', 'E:']
        messages.contains('H:/second')
        h.state(1) == WRITTEN
        h.upstreams[1].starts == 1
        h.written == ['/first', '/second']
        old.refCnt() == 0
        added.refCnt() == 0

        cleanup:
        h.close()
    }

    def 'a completion from a read callback while the response waits for the connection resumes the queue'() {
        given:
        def h = new Harness()
        h.channel.unsafe().outboundBuffer().setUserDefinedWritability(1, false)
        h.request('/first')
        h.request('/second')
        def first = h.writer(0)
        def states = []
        h.beforeRead = { ChannelHandlerContext ctx ->
            states << h.state(0)
            first.complete()
        }

        when:
        h.channel.unsafe().outboundBuffer().setUserDefinedWritability(1, true)

        then:
        h.messages() == ['H:/first', 'E:', 'H:/second']
        h.channel.checkException()
        !states.empty
        h.state(0) == WRITTEN
        h.state(1) == WRITING
        h.written == ['/first']

        cleanup:
        h.close()
    }

    def 'a refused #kind response with compression=#gzip releases its bytes without answering the accepted request'() {
        given:
        def h = new Harness()
        if (gzip) {
            enableCompression(h)
            h.gzip = true
        }
        h.request('/first')
        def accepted = h.accesses[0].handler
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

        when:
        h.channel.runPendingTasks()

        then:
        refused.refCnt() == 0
        h.written.empty
        h.handler.@outboundHandler.is(accepted)
        h.state(0) == WRITING
        upstream.discards == (kind == 'streaming' ? 1 : 0)

        when:
        h.writer(0).complete()
        def messages = h.messages()

        then:
        h.channel.checkException()
        h.channel.open
        h.written == ['/first']
        messages.first() == 'H:/first'
        messages.size() == 2

        cleanup:
        body.close()
        h.close()

        where:
        kind        | gzip
        'full'      | false
        'streaming' | false
        'full'      | true
        'streaming' | true
    }

    def 'removing the handler discards the current and the queued responses without writing the queued ones'() {
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
                states << h.state(i)
                // late signals of a discarded response are ignored, and their bytes released
                writer.complete()
                writer.error(new IOException('late error'))
                writer.addAndComplete(h.readBuffer(bytes('late')))
            }
        }
        assert h.messages() == ['H:/first']

        when:
        h.channel.pipeline().remove(h.handler)

        then:
        h.channel.checkException()
        states == [WRITTEN, WRITTEN]
        h.written == ['/first', '/second', '/third']
        (0..2).every { h.state(it) == WRITTEN }
        h.handler.@outboundHandler == null
        h.upstreams*.discards == [1, 1]
        early.refCnt() == 0
        h.fullContent.refCnt() == 0
        h.messages().empty

        cleanup:
        h.close()
    }

    def 'the final #kind write is reported once, and late signals and an abort from the report change nothing'() {
        given:
        def h = new Harness()
        h.full = kind == 'full'
        // hold the first response in the queue so that the observers are installed first
        h.channel.unsafe().outboundBuffer().setUserDefinedWritability(1, false)
        h.request('/first')
        h.full = false
        h.request('/second')
        def first = kind == 'full' ? null : h.writer(0)
        def states = []
        h.beforeWrite = { ChannelHandlerContext ctx, Object msg ->
            if (msg instanceof LastHttpContent) {
                states << h.state(0)
                first?.complete()
                first?.error(new IOException('late error'))
            }
        }
        h.onWritten = { Object attachment ->
            assert h.state(h.accesses.findIndexOf { it.@attachment == attachment }) == WRITTEN
            if (attachment == '/first') {
                h.accesses[0].abort()
                first?.complete()
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
        h.accesses[0].abort()

        then:
        h.messages() == expected
        h.channel.checkException()
        states == [kind == 'full' ? WRITING : FINISHING]
        h.written == ['/first']
        h.failed.empty
        h.channel.open
        h.state(1) == WRITING

        cleanup:
        h.close()

        where:
        kind           | expected
        'full'         | ['H:/first', 'E:full', 'H:/second']
        'final buffer' | ['H:/first', 'E:last', 'H:/second']
        'empty'        | ['H:/first', 'E:', 'H:/second']
    }

    def 'a 100 Continue is written once while its response is still to come'() {
        given:
        def h = new Harness()
        h.autoRespond = false
        h.request('/continue', true)

        when:
        2.times { h.channel.pipeline().fireChannelWritabilityChanged() }
        def response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
        response.headers().set('x-request', '/continue')
        h.accesses[0].write(response, NettyByteBodyFactory.empty())
        h.channel.pipeline().fireChannelWritabilityChanged()

        then:
        h.messages() == ['H:/continue', 'E:']
        h.channel.checkException()
        h.statuses == [100, 200]
        h.written == ['/continue']

        cleanup:
        h.close()
    }

    def 'a second #kind response after the handler was removed behind a 100 Continue is refused and released'() {
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

        when:
        h.channel.runPendingTasks()

        then:
        h.written == ['/continue']
        refused.refCnt() == 0
        upstream.discards == (kind == 'streaming' ? 1 : 0)
        h.messages().empty

        cleanup:
        body.close()
        h.close()

        where:
        kind << ['full', 'streaming']
    }

    def 'removing the handler #point releases a response written afterwards and reports its request once'() {
        given:
        def h = new Harness()
        h.beforeWrite = { ChannelHandlerContext ctx, Object msg ->
            if (msg instanceof HttpResponse && msg.status() == HttpResponseStatus.CONTINUE && removeInWrite) {
                ctx.channel().pipeline().remove(h.handler)
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

        when:
        if (!autoRespond) {
            late = bytes('late')
            h.accesses.last().write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK),
                new NettyByteBodyFactory(h.channel).adapt(late))
        }
        h.channel.runPendingTasks()
        def messages = h.messages()

        then:
        h.channel.checkException()
        h.written == (precedingResponse ? ['/first', '/late'] : ['/late'])
        h.statuses == (precedingResponse ? [200] : expectContinue ? [100] : [])
        late == null || late.refCnt() == 0
        !messages.contains('H:/late')
        !messages.any { it.startsWith('E:') }

        cleanup:
        h.close()

        where:
        point                                         | expectContinue | precedingResponse | autoRespond | removeInWrite
        'without a 100 Continue'                      | false          | false             | false       | false
        'with a current 100 Continue and no response' | true           | false             | false       | false
        'inside the write of the 100 Continue'        | true           | false             | true        | true
        'with a queued 100 Continue and no response'  | true           | true              | false       | false
        'with a queued 100 Continue and its response' | true           | true              | true        | false
    }

    def 'a response whose body completed is not written again'() {
        given:
        def h = new Harness()
        h.request('/first')
        def writer = h.writer(0)
        writer.complete()

        expect:
        h.messages() == ['H:/first', 'E:']
        h.written == ['/first']

        when:
        writer.open()
        h.channel.pipeline().fireChannelWritabilityChanged()
        writer.complete()
        h.accesses[0].abort()

        then:
        h.messages().empty
        h.channel.checkException()
        h.channel.open
        h.written == ['/first']

        cleanup:
        h.close()
    }

    def 'the compression of a response is discarded on #signal, and the response after it is not written'() {
        given:
        def h = new Harness()
        enableCompression(h)
        h.gzip = true
        h.request('/first')
        def first = h.writer(0)
        def sent = bytes('sent')
        first.add(h.readBuffer(sent))
        h.channel.runPendingTasks()
        // the encoder of the response, to observe that it is closed and drained
        EmbeddedChannel encoder = h.accesses[0].handler.compressionSession.@compressionChannel
        assert encoder != null
        h.request('/second')
        def pending = bytes('pending')
        h.writer(1).add(h.readBuffer(pending))
        h.upstreams.eachWithIndex { upstream, i ->
            upstream.onDiscard = { assert h.written.contains(i == 0 ? '/first' : '/second') }
        }
        assert h.messages().first() == 'H:/first'

        when:
        if (signal == 'error') {
            first.error(new IOException('body failed'))
        } else {
            h.channel.pipeline().remove(h.handler)
        }
        def messages = h.messages()

        then:
        h.channel.checkException()
        !encoder.open
        encoder.outboundMessages().empty
        h.written == ['/first', '/second']
        h.upstreams*.discards == [1, 1]
        sent.refCnt() == 0
        pending.refCnt() == 0
        !messages.contains('H:/second')

        cleanup:
        h.close()

        where:
        signal << ['error', 'removal']
    }

    def 'a failed response body closes the connection and discards the response after it'() {
        given:
        def h = new Harness()
        h.request('/first')
        h.request('/second')
        def first = h.writer(0)
        first.add(h.readBuffer(bytes('sent')))
        def pending = bytes('pending')
        h.writer(1).add(h.readBuffer(pending))
        assert h.messages() == ['H:/first', 'D:sent']
        def failure = new IOException('stream failed')

        when:
        first.error(failure)

        then:
        h.messages().empty
        h.channel.checkException()
        !h.channel.open
        h.written == ['/first', '/second']
        h.failed == [failure]
        h.upstreams*.discards == [1, 1]
        pending.refCnt() == 0

        cleanup:
        h.close()
    }

    def 'a close marked while the response is #stage (full=#full) closes after the response, with the header if the head is not written yet'() {
        given:
        def h = new Harness()
        String connection = null
        h.beforeWrite = { ChannelHandlerContext ctx, Object msg ->
            if (msg instanceof HttpResponse && msg.status() == HttpResponseStatus.CONTINUE) {
                ctx.channel().unsafe().outboundBuffer().setUserDefinedWritability(1, false)
            }
            if (msg instanceof HttpResponse && msg.headers().get('x-request') == '/target') {
                if (stage == 'writing its head') {
                    h.accesses.last().closeAfterWriteInEventLoop()
                }
                connection = msg.headers().get(HttpHeaderNames.CONNECTION)
            }
        }
        boolean behind = stage in ['queued', 'current but not written']
        if (behind) {
            h.request('/blocker')
        }
        h.full = full
        h.request('/target', stage == 'current but not written')
        def target = full ? null : h.writer(h.accesses.size() - 1)
        if (stage == 'current but not written') {
            h.writer(0).complete()
        }
        if (stage == 'done' && !full) {
            target.complete()
        }
        h.channel.runPendingTasks()

        when:
        if (stage != 'writing its head') {
            h.accesses.last().closeAfterWriteInEventLoop()
        }
        if (stage == 'queued') {
            h.writer(0).complete()
        } else if (stage == 'current but not written') {
            h.channel.unsafe().outboundBuffer().setUserDefinedWritability(1, true)
        }
        if (!full && stage != 'done') {
            target.complete()
        }

        then:
        h.messages() == (behind ? ['H:/blocker', 'E:'] : []) + ['H:/target', full ? 'E:full' : 'E:']
        h.channel.checkException()
        // the head can still take the header while it is on its way through the pipeline
        connection == (behind || stage == 'writing its head' ? 'close' : null)
        h.channel.open == (stage == 'done')
        h.written == (behind ? ['/blocker', '/target'] : ['/target'])

        cleanup:
        h.close()

        where:
        stage                     | full
        'queued'                  | false
        'queued'                  | true
        'current but not written' | false
        'current but not written' | true
        'writing its head'        | false
        'being written'           | false
        'done'                    | false
        'done'                    | true
    }

    def 'a close asked for by the writer after the response head closes after the last content and discards the response after it'() {
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
        h.messages() == ['H:/first', 'E:last']
        h.channel.checkException()
        connection == null
        !h.channel.open
        h.written.toSorted() == ['/first', '/second']
        h.upstreams[1].discards == 1

        cleanup:
        h.close()
    }

    private void enableCompression(Harness h) {
        h.handler.setCompressionStrategy(Stub(HttpCompressionStrategy) {
            isEnabled() >> true
            shouldCompress(_) >> true
            getCompressionLevel() >> 6
            getMaxZstdEncodeSize() >> 1024 * 1024
        })
    }

    private static ByteBuf bytes(String value) {
        Unpooled.copiedBuffer(value, StandardCharsets.UTF_8)
    }

    private static class Harness implements RequestHandler {
        final PipeliningServerHandler handler = new PipeliningServerHandler(this)
        final EmbeddedChannel channel
        final List<PipeliningServerHandler.OutboundAccessImpl> accesses = []
        final List<RecordingUpstream> upstreams = []
        final List<Object> written = []
        final List<Throwable> failed = []
        final List<Integer> statuses = []
        Closure beforeWrite = { ChannelHandlerContext ctx, Object msg -> }
        Closure beforeRead = { ChannelHandlerContext ctx -> }
        Closure onWritten = { Object attachment -> }
        boolean full
        boolean autoRespond = true
        boolean gzip
        ByteBuf fullContent

        Harness() {
            channel = new EmbeddedChannel(new ChannelOutboundHandlerAdapter() {
                @Override
                void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                    if (msg instanceof HttpResponse) {
                        statuses << msg.status().code()
                    }
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
            if (gzip) {
                request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, 'gzip')
            }
            channel.writeInbound(request)
        }

        @Override
        void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess access) {
            accesses << (PipeliningServerHandler.OutboundAccessImpl) access
            access.attachment(request.uri())
            if (request.headers().contains(HttpHeaderNames.EXPECT)) {
                // demanding the body requests the 100 Continue
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

        /**
         * @return The writer of the streamed response of a request, which takes the signals of
         * the response body
         */
        BufferConsumer writer(int index) {
            accesses[index].handler.writer
        }

        /**
         * @return The simple class name of the response state of a request
         */
        String state(int index) {
            accesses[index].@state.getClass().simpleName
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
        void responseFailed(Object attachment, Throwable cause) {
            failed << cause
        }

        @Override
        void handleUnboundError(Throwable cause) {
            throw new AssertionError(cause)
        }

        /**
         * Finish the event loop turn and summarize what was written since the last call:
         * {@code H:<uri>} for a response head, {@code E:<bytes>} for the end of a response with
         * the bytes of its body since the last entry, {@code D:<bytes>} for body bytes of a
         * response that has not ended. How the bytes are split into messages is not shown.
         */
        List<String> messages() {
            if (channel.open) {
                channel.runPendingTasks()
            }
            if (channel.open) {
                channel.flushOutbound()
            }
            List<String> result = []
            StringBuilder data = new StringBuilder()
            Object msg
            while ((msg = channel.readOutbound()) != null) {
                try {
                    boolean isContinue = msg instanceof HttpResponse && msg.status() == HttpResponseStatus.CONTINUE
                    if (msg instanceof HttpResponse && !isContinue) {
                        result << 'H:' + msg.headers().get('x-request')
                    }
                    if (msg instanceof HttpContent && !isContinue) {
                        data.append(msg.content().toString(StandardCharsets.UTF_8))
                        if (msg instanceof LastHttpContent) {
                            result << 'E:' + data
                            data.length = 0
                        }
                    }
                } finally {
                    ReferenceCountUtil.release(msg)
                }
            }
            if (data.length() > 0) {
                result << 'D:' + data
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
