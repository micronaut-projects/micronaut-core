package io.micronaut.http.client.netty

import io.micronaut.http.body.CloseableByteBody
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultHttpContent
import io.netty.handler.codec.http.DefaultHttpHeaders
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class Http1ResponseHandlerRemovalSpec extends Specification {
    def 'removing the handler releases content it buffered before the end of the response'() {
        given:
        def listener = new Listener()
        def handler = new Http1ResponseHandler(listener)
        def channel = new EmbeddedChannel(handler)
        def response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, new DefaultHttpHeaders()
                .add(HttpHeaderNames.CONTENT_LENGTH, 6))
        def first = Unpooled.copiedBuffer('foo', StandardCharsets.UTF_8)
        def second = Unpooled.copiedBuffer('bar', StandardCharsets.UTF_8)

        when:
        // one read, no read complete yet: the content stays buffered, as when the exchange ends
        // (e.g. its request body failed) in the middle of a read
        channel.writeOneInbound(response)
        channel.writeOneInbound(new DefaultHttpContent(first))
        channel.writeOneInbound(new DefaultHttpContent(second))
        channel.pipeline().remove(handler)

        then:
        listener.body == null
        first.refCnt() == 0
        second.refCnt() == 0

        cleanup:
        channel.finishAndReleaseAll()
    }

    def 'hitting the discard limit cancels its timer'() {
        given:
        def listener = new Listener()
        def handler = new Http1ResponseHandler(listener)
        def channel = new EmbeddedChannel(handler)
        channel.writeInbound(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK))
        listener.body.close()
        def timer = handler.state.timeLimit
        assert !timer.done

        when:
        def bytes = Unpooled.buffer(65537).writeZero(65537)
        channel.writeInbound(new DefaultHttpContent(bytes))

        then:
        listener.discardLimitReached
        bytes.refCnt() == 0
        timer.cancelled
        channel.advanceTimeBy(5, TimeUnit.SECONDS)
        channel.runScheduledPendingTasks() == -1

        cleanup:
        channel.finishAndReleaseAll()
    }

    private static class Listener implements Http1ResponseHandler.ResponseListener {
        CloseableByteBody body
        boolean discardLimitReached

        @Override
        void continueReceived(ChannelHandlerContext ctx) {
            throw new UnsupportedOperationException("Continue")
        }

        @Override
        void complete(HttpResponse response, CloseableByteBody body) {
            this.body = body
        }

        @Override
        void fail(ChannelHandlerContext ctx, Throwable t) {
            ctx.fireExceptionCaught(t)
        }

        @Override
        void discardLimitReached() {
            discardLimitReached = true
        }

        @Override
        void finish(ChannelHandlerContext ctx) {
        }
    }
}
