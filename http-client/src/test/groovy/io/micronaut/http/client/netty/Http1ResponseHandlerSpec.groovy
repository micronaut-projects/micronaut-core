package io.micronaut.http.client.netty

import io.micronaut.buffer.netty.NettyReadBufferFactory
import io.micronaut.core.io.buffer.ReadBuffer
import io.micronaut.http.body.AvailableByteBody
import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.body.InternalByteBody
import io.micronaut.http.body.stream.BufferConsumer
import io.micronaut.http.netty.body.StreamingNettyByteBody
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.DefaultEventLoopGroup
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.local.LocalChannel
import io.netty.handler.codec.DecoderResult
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.DefaultHttpContent
import io.netty.handler.codec.http.DefaultHttpHeaders
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.EmptyHttpHeaders
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.LastHttpContent
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class Http1ResponseHandlerSpec extends Specification {
    def simple() {
        given:
        def response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, new DefaultHttpHeaders()
                .add(HttpHeaderNames.CONTENT_LENGTH, 3))
        def listener = new SimpleListener()
        def channel = new EmbeddedChannel(new Http1ResponseHandler(listener))

        when:
        channel.writeInbound(
                response,
                new DefaultHttpContent(Unpooled.copiedBuffer("foo", StandardCharsets.UTF_8)),
                LastHttpContent.EMPTY_LAST_CONTENT
        )

        then:
        listener.response == response
        listener.body instanceof AvailableByteBody
        listener.body.toString(StandardCharsets.UTF_8) == "foo"

        cleanup:
        listener.body.close()
        channel.checkException()
    }

    def "multiple buffered"() {
        given:
        def response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, new DefaultHttpHeaders()
                .add(HttpHeaderNames.CONTENT_LENGTH, 3))
        def listener = new SimpleListener()
        def channel = new EmbeddedChannel(new Http1ResponseHandler(listener))

        when:
        channel.writeInbound(
                response,
                new DefaultHttpContent(Unpooled.copiedBuffer("f", StandardCharsets.UTF_8)),
                new DefaultHttpContent(Unpooled.copiedBuffer("o", StandardCharsets.UTF_8)),
                new DefaultHttpContent(Unpooled.copiedBuffer("o", StandardCharsets.UTF_8)),
                LastHttpContent.EMPTY_LAST_CONTENT
        )

        then:
        listener.response == response
        listener.body instanceof AvailableByteBody
        listener.body.toString(StandardCharsets.UTF_8) == "foo"

        cleanup:
        listener.body.close()
        channel.checkException()
    }

    def "single message"() {
        given:
        def response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK,
                Unpooled.copiedBuffer("foo", StandardCharsets.UTF_8),
                new DefaultHttpHeaders().add(HttpHeaderNames.CONTENT_LENGTH, 3),
                EmptyHttpHeaders.INSTANCE
        )
        def listener = new SimpleListener()
        def channel = new EmbeddedChannel(new Http1ResponseHandler(listener))

        when:
        channel.writeInbound(response)

        then:
        listener.response == response
        listener.body instanceof AvailableByteBody
        listener.body.toString(StandardCharsets.UTF_8) == "foo"

        cleanup:
        listener.body.close()
        channel.checkException()
    }

    def empty() {
        given:
        def response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, new DefaultHttpHeaders()
                .add(HttpHeaderNames.CONTENT_LENGTH, 0))
        def listener = new SimpleListener()
        def channel = new EmbeddedChannel(new Http1ResponseHandler(listener))

        when:
        channel.writeInbound(
                response,
                LastHttpContent.EMPTY_LAST_CONTENT
        )

        then:
        listener.response == response
        listener.body instanceof AvailableByteBody
        listener.body.toByteArray().length == 0

        cleanup:
        listener.body.close()
    }

    def "continue"() {
        given:
        def response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, new DefaultHttpHeaders()
                .add(HttpHeaderNames.CONTENT_LENGTH, 3))
        boolean continueReceived = false
        def listener = new SimpleListener() {
            @Override
            void continueReceived(ChannelHandlerContext ctx) {
                continueReceived = true
            }
        }
        def channel = new EmbeddedChannel(new Http1ResponseHandler(listener))

        when:
        channel.writeInbound(
                new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE),
                LastHttpContent.EMPTY_LAST_CONTENT,
                response,
                new DefaultHttpContent(Unpooled.copiedBuffer("foo", StandardCharsets.UTF_8)),
                LastHttpContent.EMPTY_LAST_CONTENT
        )

        then:
        continueReceived
        listener.response == response
        listener.body instanceof AvailableByteBody
        listener.body.toString(StandardCharsets.UTF_8) == "foo"

        cleanup:
        listener.body.close()
        channel.checkException()
    }

    def "simple streaming"() {
        given:
        def response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, new DefaultHttpHeaders()
                .add(HttpHeaderNames.CONTENT_LENGTH, 3))
        def listener = new SimpleListener()
        def channel = new EmbeddedChannel(new Http1ResponseHandler(listener))

        when:
        channel.writeInbound(
                response,
                new DefaultHttpContent(Unpooled.copiedBuffer("f", StandardCharsets.UTF_8))
        )
        then:
        listener.response == response
        listener.body.expectedLength().getAsLong() == 3

        when:
        def buffered = InternalByteBody.bufferFlow(listener.body)
        then:
        buffered.tryComplete() == null

        when:
        channel.writeInbound(new DefaultHttpContent(Unpooled.copiedBuffer("oo", StandardCharsets.UTF_8)))
        then:
        buffered.tryComplete() == null

        when:
        channel.writeInbound(LastHttpContent.EMPTY_LAST_CONTENT)
        then:
        buffered.tryCompleteValue().toString(StandardCharsets.UTF_8) == "foo"

        cleanup:
        channel.checkException()
    }

    def "backpressure"() {
        given:
        def response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, new DefaultHttpHeaders()
                .add(HttpHeaderNames.CONTENT_LENGTH, 3))
        def listener = new SimpleListener()
        def counter = new ReadCounter()
        def channel = new EmbeddedChannel(counter, new Http1ResponseHandler(listener))

        expect:
        counter.reads == 1

        when:
        channel.writeInbound(response)
        then:
        listener.response == response
        counter.reads == 1

        when:
        def completed = false
        def buffer = Unpooled.compositeBuffer()
        def upstream = ((StreamingNettyByteBody) listener.body).primary(new BufferConsumer(){
            @Override
            void add(ReadBuffer buf) {
                buffer.addComponent(true, NettyReadBufferFactory.toByteBuf(buf))
            }

            @Override
            void complete() {
                completed = true
            }

            @Override
            void error(Throwable e) {
                throw e
            }
        })
        then:
        buffer.toString(StandardCharsets.UTF_8) == ""
        counter.reads == 1

        when:
        upstream.start()
        then:
        counter.reads == 2

        when:
        channel.writeInbound(new DefaultHttpContent(Unpooled.copiedBuffer("fo", StandardCharsets.UTF_8)))
        then:
        buffer.toString(StandardCharsets.UTF_8) == "fo"
        counter.reads == 2

        when:
        upstream.onBytesConsumed(1)
        then:
        counter.reads == 2

        when:
        upstream.onBytesConsumed(1)
        then:
        counter.reads == 3

        when:
        channel.writeInbound(new DefaultHttpContent(Unpooled.copiedBuffer("o", StandardCharsets.UTF_8)))
        then:
        buffer.toString(StandardCharsets.UTF_8) == "foo"
        counter.reads == 3

        when:
        channel.writeInbound(LastHttpContent.EMPTY_LAST_CONTENT)
        then:
        completed
        // the response is done, the idle handler does not request more data
        counter.reads == 3

        cleanup:
        channel.checkException()
    }

    def "decode error"() {
        given:
        def exc = new Exception("test")
        def response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
        response.setDecoderResult(DecoderResult.failure(exc))
        Throwable seen = null
        def listener = new SimpleListener() {
            @Override
            void fail(ChannelHandlerContext ctx, Throwable t) {
                seen = t
            }
        }
        def channel = new EmbeddedChannel(new Http1ResponseHandler(listener))

        when:
        channel.writeInbound(response)

        then:
        seen == exc

        cleanup:
        channel.checkException()
    }

    def "sequential requests on one handler"() {
        given:
        def tail = new TailRecorder()
        def handler = new Http1ResponseHandler()
        def channel = new EmbeddedChannel(handler, tail)

        expect:
        handler.idle

        when:
        // without a request, messages pass through like there was no handler
        def stray = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
        channel.writeInbound(stray)
        then:
        tail.messages == [stray]
        handler.idle

        when:
        def listener1 = new SimpleListener()
        def response1 = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, new DefaultHttpHeaders()
                .add(HttpHeaderNames.CONTENT_LENGTH, 3))
        handler.startRequest(listener1)
        then:
        !handler.idle

        when:
        channel.writeInbound(
                response1,
                new DefaultHttpContent(Unpooled.copiedBuffer("foo", StandardCharsets.UTF_8)),
                LastHttpContent.EMPTY_LAST_CONTENT
        )
        then:
        listener1.response == response1
        listener1.body.toString(StandardCharsets.UTF_8) == "foo"
        listener1.finished
        handler.idle
        tail.messages == [stray]

        when:
        def listener2 = new SimpleListener()
        def response2 = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK,
                Unpooled.copiedBuffer("bar", StandardCharsets.UTF_8),
                new DefaultHttpHeaders().add(HttpHeaderNames.CONTENT_LENGTH, 3),
                EmptyHttpHeaders.INSTANCE
        )
        handler.startRequest(listener2)
        channel.writeInbound(response2)
        then:
        listener2.response == response2
        listener2.body.toString(StandardCharsets.UTF_8) == "bar"
        listener2.finished
        handler.idle
        tail.messages == [stray]

        when:
        handler.startRequest(new SimpleListener())
        handler.startRequest(new SimpleListener())
        then:
        // a second request cannot start while the first is in progress
        thrown(IllegalStateException)

        cleanup:
        listener1.body.close()
        listener2.body.close()
    }

    def "failure before the response resets the handler"() {
        given:
        def handler = new Http1ResponseHandler()
        def channel = new EmbeddedChannel(handler)
        Throwable seen = null
        def listener = new SimpleListener() {
            @Override
            void fail(ChannelHandlerContext ctx, Throwable t) {
                seen = t
            }
        }
        handler.startRequest(listener)

        when:
        def exc = new Exception("test")
        channel.pipeline().fireExceptionCaught(exc)
        then:
        seen == exc
        listener.finished
        handler.idle
        channel.checkException()
    }

    def "writability changes are forwarded to the listener of the request in progress"() {
        given:
        def tail = new TailRecorder()
        def handler = new Http1ResponseHandler()
        def channel = new EmbeddedChannel(handler, tail)
        int writabilityChanges = 0
        def listener = new SimpleListener() {
            @Override
            void writabilityChanged(ChannelHandlerContext ctx) {
                writabilityChanges++
            }
        }

        when: "no request is in progress"
        channel.pipeline().fireChannelWritabilityChanged()
        then: "the event only passes through"
        writabilityChanges == 0
        tail.writabilityChanges == 1

        when: "a request is in progress"
        handler.startRequest(listener)
        channel.pipeline().fireChannelWritabilityChanged()
        then: "the listener is notified, and the event passes through"
        writabilityChanges == 1
        tail.writabilityChanges == 2

        when: "the request is done"
        channel.writeInbound(new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK,
                Unpooled.EMPTY_BUFFER,
                new DefaultHttpHeaders().add(HttpHeaderNames.CONTENT_LENGTH, 0),
                EmptyHttpHeaders.INSTANCE
        ))
        channel.pipeline().fireChannelWritabilityChanged()
        then: "the listener of the finished request is not notified anymore"
        listener.finished
        handler.idle
        writabilityChanges == 1
        tail.writabilityChanges == 3

        when: "a listener that does not care about writability is in progress"
        def plainListener = new SimpleListener()
        handler.startRequest(plainListener)
        channel.pipeline().fireChannelWritabilityChanged()
        then:
        tail.writabilityChanges == 4
        !handler.idle

        cleanup:
        listener.body?.close()
        channel.checkException()
    }

    def "a request cannot start off the event loop or without a channel"() {
        given:
        def group = new DefaultEventLoopGroup(1)
        def channel = new LocalChannel()
        group.register(channel).sync()
        def handler = new Http1ResponseHandler()

        when: "the handler is not in a pipeline"
        handler.startRequest(new SimpleListener())
        then:
        def notAdded = thrown(IllegalStateException)
        notAdded.message == "Not added to a channel"

        when: "the request is started from another thread than the event loop of the channel"
        channel.eventLoop().submit { channel.pipeline().addLast(handler) }.sync()
        handler.startRequest(new SimpleListener())
        then:
        def offLoop = thrown(IllegalStateException)
        offLoop.message == "Not on event loop"
        handler.idle

        when: "the request is started on the event loop"
        channel.eventLoop().submit { handler.startRequest(new SimpleListener()) }.sync()
        then:
        !handler.idle

        cleanup:
        channel.close().sync()
        group.shutdownGracefully().sync()
    }

    private static final class TailRecorder extends ChannelInboundHandlerAdapter {
        final List<Object> messages = []
        int writabilityChanges = 0

        @Override
        void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            messages.add(msg)
        }

        @Override
        void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
            writabilityChanges++
        }
    }

    private static final class ReadCounter extends ChannelOutboundHandlerAdapter {
        int reads = 0

        @Override
        void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            ctx.channel().config().autoRead = false
        }

        @Override
        void read(ChannelHandlerContext ctx) throws Exception {
            reads++
            super.read(ctx)
        }
    }

    private static class SimpleListener implements Http1ResponseHandler.ResponseListener {
        HttpResponse response
        CloseableByteBody body
        boolean finished

        @Override
        void continueReceived(ChannelHandlerContext ctx) {
            throw new UnsupportedOperationException("Continue")
        }

        @Override
        void complete(HttpResponse response, CloseableByteBody body) {
            this.response = response
            this.body = body
        }

        @Override
        void fail(ChannelHandlerContext ctx, Throwable t) {
            ctx.fireExceptionCaught(t)
        }

        @Override
        void finish(ChannelHandlerContext ctx) {
            finished = true
        }
    }
}
