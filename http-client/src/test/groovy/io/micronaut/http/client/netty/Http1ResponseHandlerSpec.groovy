package io.micronaut.http.client.netty

import io.micronaut.http.body.AvailableByteBody
import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.body.InternalByteBody
import io.micronaut.http.netty.body.ByteBufConsumer
import io.micronaut.http.netty.body.StreamingNettyByteBody
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.embedded.EmbeddedChannel
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
        def upstream = ((StreamingNettyByteBody) listener.body).primary(new ByteBufConsumer() {
            @Override
            void add(ByteBuf buf) {
                buffer.addComponent(true, buf)
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
        counter.reads == 4

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

    private static class ReadCounter extends ChannelOutboundHandlerAdapter {
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
        }
    }
}
