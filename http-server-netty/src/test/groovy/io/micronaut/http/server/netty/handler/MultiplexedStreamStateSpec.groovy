package io.micronaut.http.server.netty.handler

import io.micronaut.http.body.AvailableByteBody
import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.body.stream.BaseSharedBuffer.IncorrectContentLengthException
import io.micronaut.http.body.stream.BodySizeLimits
import io.micronaut.http.body.stream.BufferConsumer
import io.micronaut.http.netty.body.NettyByteBodyFactory
import io.micronaut.http.netty.body.StreamingNettyByteBody
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.netty.channel.DefaultEventLoop
import io.netty.channel.EventLoop
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultHttpRequest
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.DefaultHttpHeaders
import io.netty.handler.codec.http.HttpHeaders
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http2.DefaultHttp2DataFrame
import io.netty.handler.codec.http2.DefaultHttp2GoAwayFrame
import io.netty.handler.codec.http2.DefaultHttp2Headers
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame
import io.netty.handler.codec.http2.DefaultHttp2ResetFrame
import io.netty.handler.codec.http2.Http2ChannelDuplexHandler
import io.netty.handler.codec.http2.Http2Error
import io.netty.handler.codec.http2.Http2FrameCodecBuilder
import io.netty.handler.codec.http2.Http2FrameStream
import spock.lang.Specification

import java.nio.channels.ClosedChannelException
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletionException
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

/**
 * The request, response and end state of a multiplexed stream, driven directly and through the
 * HTTP/2 connection handler.
 */
class MultiplexedStreamStateSpec extends Specification {
    private static final List<String> STATES = ['awaiting', 'headers', 'buffering', 'streaming', 'ended',
                                              'stream ended', 'failed', 'buffer failed', 'stream failed']
    /** The states with request headers and without an accepted request. A reset does not end them: only the close of the stream does. */
    private static final List<String> PENDING = ['headers', 'buffering', 'failed', 'buffer failed']
    private static final List<String> EVENTS = ['data', 'last data', 'trailers', 'read complete', 'reset', 'error goaway', 'close']

    private static class Accepted {
        CloseableByteBody body
        OutboundAccess outbound
    }

    private static class Requests implements RequestHandler {
        final List<Accepted> accepted = []
        final List<Object> disposed = []

        @Override
        void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outbound) {
            accepted.add(new Accepted(body: body, outbound: outbound))
        }

        @Override
        void responseWritten(Object attachment) {
            disposed.add(attachment)
        }

        @Override
        void handleUnboundError(Throwable cause) {
            throw new AssertionError(cause)
        }
    }

    /** Direct events also cover states and event orders the HTTP/2 decoder normally filters. */
    private static class Handler extends MultiplexedServerHandler {
        Handler(RequestHandler requests) {
            super(requests)
        }

        @Override
        void flush() {
        }

        void attach(EmbeddedChannel channel) {
            connection = ConnectionContext.of(channel.pipeline().firstContext(), {} as Runnable)
        }

        TestStream newStream() {
            new TestStream(requiredConnection())
        }

        class TestStream extends MultiplexedServerHandler.MultiplexedStream {
            int consumed
            int resets

            TestStream(ConnectionContext connection) {
                super(Handler.this, connection, 3)
            }

            @Override
            void notifyDataConsumed(int n) {
                consumed += n
            }

            @Override
            boolean reset(Throwable cause) {
                resets++
                onRstStreamRead(new ClosedChannelException())
                true
            }

            @Override
            void closeInput() {
            }

            @Override
            void writeHeaders(HttpResponse headers, boolean endStream, ChannelPromise promise) {
                promise.trySuccess()
            }

            @Override
            void writeData0(ByteBuf data, boolean endStream, ChannelPromise promise) {
                data.release()
                promise.trySuccess()
            }

            @Override
            void writeTrailers(HttpHeaders trailers, ChannelPromise promise) {
                promise.trySuccess()
            }
        }
    }

    def 'a rejected #kind write on a #state stream releases the content and reports the rejection to the caller'() {
        given:
        def loop = new DefaultEventLoop()
        def requests = new Requests()
        def handler = new Handler(requests)
        EmbeddedChannel channel
        Handler.TestStream stream
        loop.submit({
            channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter()) {
                @Override
                EventLoop eventLoop() { loop }
            }
            handler.attach(channel)
            stream = handler.newStream()
            stream.onHeadersRead(new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/'), true)
            if (state == 'reset') {
                stream.onRstStreamRead(new ClosedChannelException())
            } else {
                stream.discardBufferedContent()
            }
            if (state == 'finished') {
                stream.writeHeadResponse(response())
            }
        } as Runnable).get(5, TimeUnit.SECONDS)
        loop.shutdownGracefully(0, 0, TimeUnit.SECONDS).sync()
        def content = bytes('response')

        when:
        if (kind == 'full') {
            stream.write(response(), new NettyByteBodyFactory(channel).adapt(content))
        } else {
            content.release()
            stream.writeHeadResponse(response())
        }

        then: 'the event loop is shut down: the caller is told, and nothing is finished twice'
        thrown(RejectedExecutionException)
        content.refCnt() == 0
        requests.disposed.size() == (state == 'finished' ? 1 : 0)

        cleanup:
        if (content?.refCnt() > 0) { content.release() }
        requests.accepted.each { it.body.close() }
        channel?.finishAndReleaseAll()
        loop.shutdownGracefully(0, 0, TimeUnit.SECONDS).sync()

        where:
        [kind, state] << [['full', 'head'], ['closed', 'reset', 'finished']].combinations()
    }

    def 'abort after close sends a reset only when none was previously received (#resetFirst)'() {
        given:
        def requests = new Requests()
        def handler = new Handler(requests)
        def channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter())
        handler.attach(channel)
        def stream = handler.newStream()
        stream.onHeadersRead(new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/'), true)
        if (resetFirst) {
            stream.onRstStreamRead(new ClosedChannelException())
        }
        stream.discardBufferedContent()

        when:
        stream.abort()
        stream.abort()

        then:
        stream.resets == (resetFirst ? 0 : 1)
        requests.disposed.size() == 1

        cleanup:
        requests.accepted.each { it.body.close() }
        channel.finishAndReleaseAll()

        where:
        resetFirst << [false, true]
    }

    def 'input #state handles #event'() {
        given:
        def requests = new Requests()
        def handler = new Handler(requests)
        def channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter())
        handler.attach(channel)
        def stream = handler.newStream()
        if (state != 'awaiting') {
            stream.onHeadersRead(new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, '/'), state == 'ended')
        }
        if (state in ['buffering', 'buffer failed']) {
            stream.onDataRead(bytes('old'), false)
        }
        if (state in ['streaming', 'stream ended', 'stream failed']) {
            stream.devolveToStreaming()
        }
        if (state == 'stream ended') {
            stream.onDataRead(bytes('old'), true)
        }
        if (state in ['failed', 'buffer failed', 'stream failed']) {
            stream.onRstStreamRead(new ClosedChannelException())
        }
        int initiallyAccepted = requests.accepted.size()
        ByteBuf data = null
        Throwable failure = null

        when:
        try {
            switch (event) {
                case 'data':
                case 'last data':
                    data = bytes('new')
                    stream.onDataRead(data, event == 'last data')
                    break
                case 'trailers': stream.onTrailersRead(new DefaultHttpHeaders(), true); break
                case 'read complete': stream.devolveToStreaming(); break
                case 'reset': stream.onRstStreamRead(new ClosedChannelException()); break
                case 'error goaway': stream.onGoAwayRead(new ClosedChannelException()); break
                case 'close': stream.discardBufferedContent(); break
            }
        } catch (Throwable t) {
            failure = t
        }

        then:
        (failure instanceof IllegalStateException) == (event in ['data', 'last data'] && state in ['awaiting', 'ended', 'stream ended'])
        (failure instanceof IncorrectContentLengthException) == (event in ['data', 'last data'] && state == 'stream ended')
        failure == null || failure instanceof IllegalStateException
        requests.accepted.size() == initiallyAccepted + (acceptedBy(state, event) ? 1 : 0)
        requests.disposed.empty
        if (acceptedBy(state, event) && event != 'read complete') {
            assert requests.accepted[0].body instanceof AvailableByteBody
            assert text((AvailableByteBody) requests.accepted[0].body) == (state in ['buffering', 'buffer failed'] ? 'old' : '') + (event == 'last data' ? 'new' : '')
        }
        if (state in ['streaming', 'stream failed'] && event in ['reset', 'error goaway', 'close']) {
            assert requests.accepted[0].body.buffer().completedExceptionally
        }

        when: 'a later read complete accepts a request that is not accepted yet, unless the stream is closed'
        if (event in ['reset', 'error goaway', 'close']) {
            stream.devolveToStreaming()
        }

        then:
        !(event == 'close') || requests.accepted.size() == initiallyAccepted
        !(event in ['reset', 'error goaway']) || requests.accepted.size() == initiallyAccepted + (state in PENDING ? 1 : 0)
        !(event in ['reset', 'error goaway', 'close'] && state in ['awaiting'] + PENDING) || stream.consumed == 0

        cleanup:
        // An invalid DATA after an already accepted full request remains the caller's buffer.
        if (failure != null && data?.refCnt() > 0) {
            data.release()
        }
        stream.discardBufferedContent()
        requests.accepted.each { it.body.close() }
        assert data == null || data.refCnt() == 0
        channel.finishAndReleaseAll()

        where:
        [state, event] << [STATES, EVENTS].combinations()
    }

    def 'off-loop #kind on a reset stream finishes once on the event loop'() {
        given:
        def loop = new DefaultEventLoop()
        def requests = new Requests() {
            @Override
            void responseWritten(Object attachment) {
                assert loop.inEventLoop()
                super.responseWritten(attachment)
            }
        }
        def handler = new Handler(requests)
        EmbeddedChannel channel
        Handler.TestStream stream
        CloseableByteBody responseBody
        def content = bytes('response')
        loop.submit({
            channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter()) {
                @Override
                EventLoop eventLoop() {
                    loop
                }
            }
            handler.attach(channel)
            stream = handler.newStream()
            stream.onHeadersRead(new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, '/'), false)
            stream.devolveToStreaming()
            stream.onRstStreamRead(new ClosedChannelException())
            def factory = new NettyByteBodyFactory(channel)
            if (kind == 'streaming') {
                def buffer = factory.createStreamingBuffer(BodySizeLimits.UNLIMITED, new BufferConsumer.Upstream() {
                    @Override
                    void onBytesConsumed(long n) {
                    }
                })
                buffer.add(factory.readBufferFactory().adapt(content))
                responseBody = new StreamingNettyByteBody(buffer)
            } else if (kind == 'full') {
                responseBody = factory.adapt(content)
            }
        } as Runnable).get(10, TimeUnit.SECONDS)
        assert !loop.inEventLoop()

        when:
        switch (kind) {
            case 'full':
            case 'streaming': stream.write(response(), responseBody); break
            case 'head': stream.writeHeadResponse(response()); content.release(); break
            case 'abort': stream.abort(); content.release(); break
        }
        loop.submit({ stream.abort() } as Runnable).get(10, TimeUnit.SECONDS)

        then:
        requests.disposed.size() == 1
        content.refCnt() == 0

        cleanup:
        loop.submit({
            requests.accepted.each { it.body.close() }
            responseBody?.close()
            channel?.finishAndReleaseAll()
        } as Runnable).get(10, TimeUnit.SECONDS)
        loop.shutdownGracefully(0, 0, TimeUnit.SECONDS).get(10, TimeUnit.SECONDS)

        where:
        kind << ['full', 'streaming', 'head', 'abort']
    }

    private static class H2 implements AutoCloseable {
        final Requests requests = new Requests()
        final Http2ServerHandler.ConnectionHandler handler = new Http2ServerHandler.ConnectionHandlerBuilder(requests).build()
        final EmbeddedChannel server = new EmbeddedChannel(handler)
        final Http2ChannelDuplexHandler duplex = new Http2ChannelDuplexHandler() {}
        final EmbeddedChannel client = new EmbeddedChannel(Http2FrameCodecBuilder.forClient().build(), duplex)

        Http2FrameStream headers() {
            def stream = duplex.newStream()
            client.writeOutbound(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers()
                    .method('POST').scheme('http').authority('example.com').path('/'), false).stream(stream))
            stream
        }

        void data(Http2FrameStream stream, String value, boolean last = false) {
            client.writeOutbound(new DefaultHttp2DataFrame(bytes(value), last).stream(stream))
        }

        ByteBuf read(boolean complete = true) {
            ByteBuf packet = Unpooled.buffer()
            ByteBuf msg
            while ((msg = client.readOutbound()) != null) {
                packet.writeBytes(msg)
                msg.release()
            }
            server.writeOneInbound(packet)
            if (complete) {
                server.pipeline().fireChannelReadComplete()
            }
            server.checkException()
            packet
        }

        int unconsumed() {
            def connection = handler.connection()
            connection.local().flowController().unconsumedBytes(connection.connectionStream())
        }

        @Override
        void close() {
            requests.accepted.each { it.body.close() }
            server.unsafe().close(server.unsafe().voidPromise())
            server.runPendingTasks()
            server.finishAndReleaseAll()
            client.finishAndReleaseAll()
            server.checkException()
            client.checkException()
        }
    }

    def 'RST before accept settles only its own bytes, with #buffered buffered'() {
        given:
        def h2 = new H2()
        def reset = h2.headers()
        if (buffered) {
            h2.data(reset, 'reset')
        }
        def live = h2.headers()
        h2.data(live, 'live')
        def packet = h2.read(false)
        assert h2.unconsumed() == (buffered ? 9 : 4)

        when:
        h2.client.writeOutbound(new DefaultHttp2ResetFrame(Http2Error.CANCEL).stream(reset))
        h2.read(false)

        then:
        h2.unconsumed() == 4
        h2.requests.accepted.empty

        when:
        h2.server.pipeline().fireChannelReadComplete()
        def body = h2.requests.accepted.getFirst().body.buffer()
        h2.data(live, '!', true)
        h2.read()

        then:
        text(body.join()) == 'live!'
        h2.unconsumed() == 0
        packet.refCnt() == 0

        cleanup:
        h2.close()

        where:
        buffered << [false, true]
    }

    def 'RST after accept leaves disposal to the running handler'() {
        given:
        def h2 = new H2()
        def stream = h2.headers()
        h2.read()
        def accepted = h2.requests.accepted.getFirst()
        def attachment = new Object()
        accepted.outbound.attachment(attachment)
        def body = accepted.body.buffer()

        when:
        h2.client.writeOutbound(new DefaultHttp2ResetFrame(Http2Error.CANCEL).stream(stream))
        h2.read()

        then:
        h2.requests.disposed.empty
        body.completedExceptionally

        when:
        body.join()

        then:
        def error = thrown(CompletionException)
        error.cause instanceof ClosedChannelException

        when: 'the handler eventually responds on the reset stream'
        accepted.outbound.write(response(), NettyByteBodyFactory.empty())
        accepted.outbound.abort()
        accepted.outbound.writeHeadResponse(response())

        then:
        h2.requests.disposed == [attachment]
        h2.server.open

        cleanup:
        h2.close()
    }

    def '#error GOAWAY before acceptance still accepts the request at read complete'() {
        given:
        def h2 = new H2()
        def stream = h2.headers()
        h2.data(stream, 'body')
        h2.client.writeOutbound(new DefaultHttp2GoAwayFrame(error))

        when:
        def packet = h2.read()

        then:
        h2.requests.accepted.size() == 1
        h2.unconsumed() == 4
        packet.refCnt() > 0

        when: 'the connection closes while the body is still streaming'
        def body = h2.requests.accepted.getFirst().body.buffer()
        h2.server.unsafe().close(h2.server.unsafe().voidPromise())
        h2.server.runPendingTasks()

        then: 'the body does not wait for data that cannot arrive any more'
        body.completedExceptionally
        h2.requests.disposed.empty

        cleanup:
        h2.close()
        assert packet.refCnt() == 0

        where:
        error << [Http2Error.NO_ERROR, Http2Error.CANCEL]
    }

    def '#error GOAWAY after acceptance #outcome the body'() {
        given:
        def h2 = new H2()
        def stream = h2.headers()
        h2.read()
        def body = h2.requests.accepted.getFirst().body.buffer()

        when:
        h2.client.writeOutbound(new DefaultHttp2GoAwayFrame(error))
        h2.read()

        then:
        body.completedExceptionally == (error != Http2Error.NO_ERROR)
        h2.requests.disposed.empty

        when:
        if (error == Http2Error.NO_ERROR) {
            h2.data(stream, 'done', true)
            h2.read()
        }

        then:
        error != Http2Error.NO_ERROR || text(body.join()) == 'done'

        cleanup:
        h2.close()

        where:
        error                 | outcome
        Http2Error.NO_ERROR   | 'continues'
        Http2Error.CANCEL     | 'fails'
    }

    def 'discard on an open stream preserves window accounting until close'() {
        given:
        def h2 = new H2()
        def stream = h2.headers()
        h2.data(stream, 'first')
        def packet = h2.read()
        assert h2.unconsumed() == 5

        when: 'discard releases the buffered body without acknowledging it again'
        h2.requests.accepted.getFirst().body.close()

        then:
        packet.refCnt() == 0
        h2.unconsumed() == 5

        when: 'subsequent discarded data is consumed immediately while the stream stays open'
        h2.data(stream, 'later')
        h2.read()

        then:
        h2.unconsumed() == 5

        when: 'Netty settles the remaining bytes on reset'
        h2.client.writeOutbound(new DefaultHttp2ResetFrame(Http2Error.CANCEL).stream(stream))
        h2.read()

        then:
        h2.unconsumed() == 0
        h2.requests.disposed.empty

        cleanup:
        h2.close()
    }

    private static boolean acceptedBy(String state, String event) {
        state in PENDING && (event in ['last data', 'read complete'] || event == 'trailers' && state in ['headers', 'buffering'])
    }

    private static ByteBuf bytes(String value) {
        Unpooled.copiedBuffer(value, StandardCharsets.UTF_8)
    }

    private static String text(AvailableByteBody body) {
        try {
            new String(body.toByteArray(), StandardCharsets.UTF_8)
        } finally {
            body.close()
        }
    }

    private static HttpResponse response() {
        new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
    }
}
