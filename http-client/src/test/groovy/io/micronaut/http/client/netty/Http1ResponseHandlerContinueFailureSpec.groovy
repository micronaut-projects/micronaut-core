package io.micronaut.http.client.netty

import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.client.exceptions.ResponseClosedException
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.DecoderResult
import io.netty.handler.codec.http.DefaultHttpContent
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import spock.lang.Specification

import java.nio.charset.StandardCharsets

/**
 * A failure after the head of a 100 Continue response, but before its {@code LastHttpContent},
 * fails the response.
 */
class Http1ResponseHandlerContinueFailureSpec extends Specification {

    def "an exception after a 100 Continue head fails the response"() {
        given:
        def listener = new RecordingListener()
        def channel = new EmbeddedChannel(new Http1ResponseHandler(listener))
        def exc = new Exception("test")

        when:
        channel.writeInbound(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE))
        channel.pipeline().fireExceptionCaught(exc)

        then:
        listener.continueReceived
        listener.failures == [exc]
        listener.finishCount == 1
        listener.response == null

        cleanup:
        channel.finishAndReleaseAll()
    }

    def "a connection close after a 100 Continue head fails the response"() {
        given:
        def listener = new RecordingListener()
        def channel = new EmbeddedChannel(new Http1ResponseHandler(listener))

        when:
        channel.writeInbound(
                new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE),
                new DefaultHttpContent(Unpooled.copiedBuffer("x", StandardCharsets.UTF_8))
        )
        channel.close()

        then:
        listener.continueReceived
        listener.failures.size() == 1
        listener.failures[0] instanceof ResponseClosedException
        listener.finishCount == 1
        listener.response == null

        cleanup:
        channel.finishAndReleaseAll()
    }

    def "a decode error after a 100 Continue head fails the response"() {
        given:
        def listener = new RecordingListener()
        def channel = new EmbeddedChannel(new Http1ResponseHandler(listener))
        def exc = new Exception("test")
        def content = new DefaultHttpContent(Unpooled.EMPTY_BUFFER)
        content.setDecoderResult(DecoderResult.failure(exc))

        when:
        channel.writeInbound(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE), content)

        then:
        listener.continueReceived
        listener.failures == [exc]
        listener.finishCount == 1

        when: 'the connection closes afterwards'
        channel.close()

        then: 'the response is not failed again'
        listener.failures == [exc]
        listener.finishCount == 1

        cleanup:
        channel.finishAndReleaseAll()
    }

    private static class RecordingListener implements Http1ResponseHandler.ResponseListener {
        boolean continueReceived
        HttpResponse response
        final List<Throwable> failures = []
        int finishCount

        @Override
        void continueReceived(ChannelHandlerContext ctx) {
            continueReceived = true
        }

        @Override
        void complete(HttpResponse response, CloseableByteBody body) {
            this.response = response
            body.close()
        }

        @Override
        void fail(ChannelHandlerContext ctx, Throwable t) {
            failures.add(t)
        }

        @Override
        void finish(ChannelHandlerContext ctx) {
            finishCount++
        }
    }
}
