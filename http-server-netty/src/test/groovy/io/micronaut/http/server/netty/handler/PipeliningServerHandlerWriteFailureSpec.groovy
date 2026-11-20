package io.micronaut.http.server.netty.handler

import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.body.stream.BodySizeLimits
import io.micronaut.http.body.stream.BufferConsumer
import io.micronaut.http.netty.body.NettyByteBodyFactory
import io.micronaut.http.netty.body.StreamingNettyByteBody
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.HttpContent
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.util.ReferenceCountUtil
import spock.lang.Specification

import java.nio.charset.StandardCharsets

/**
 * A failure of the body of an HTTP/1 response cuts the response: what was written goes out, the
 * request is reported once and the connection is closed.
 */
class PipeliningServerHandlerWriteFailureSpec extends Specification {

    def 'a failed response whose body throws from its discard is still reported and closes the connection'() {
        given:
        def written = []
        def unbound = []
        OutboundAccess outbound = null
        def channel = new EmbeddedChannel(new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess access) {
                body.close()
                access.attachment(request.uri())
                outbound = access
            }

            @Override
            void responseWritten(Object attachment) {
                written << attachment
            }

            @Override
            void handleUnboundError(Throwable cause) {
                unbound << cause
            }
        }))
        def factory = new NettyByteBodyFactory(channel)
        def failure = new IllegalStateException('discard failed')
        int discards = 0
        def responseBody = factory.createStreamingBuffer(BodySizeLimits.UNLIMITED, new BufferConsumer.Upstream() {
            @Override
            void onBytesConsumed(long bytesConsumed) {
            }

            @Override
            void allowDiscard() {
                discards++
                throw failure
            }
        })
        ByteBuf sent = Unpooled.copiedBuffer('sent', StandardCharsets.UTF_8)

        when: 'the response starts, and its body fails after the first piece'
        channel.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/'))
        outbound.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), new StreamingNettyByteBody(responseBody))
        responseBody.add(factory.readBufferFactory().adapt(sent))
        channel.runPendingTasks()
        Throwable thrown = null
        try {
            responseBody.error(new IOException('body failed'))
            channel.runPendingTasks()
            channel.checkException()
        } catch (Throwable t) {
            thrown = t
        }
        List<Object> messages = []
        Object msg
        while ((msg = channel.readOutbound()) != null) {
            messages << msg
        }

        then: 'the failure of the discard does not keep the connection open'
        thrown.is(failure)
        discards == 1
        written == ['/']
        unbound.empty
        !channel.open
        messages.size() == 2
        messages[0] instanceof HttpResponse
        ((HttpContent) messages[1]).content().toString(StandardCharsets.UTF_8) == 'sent'

        cleanup:
        messages.each { ReferenceCountUtil.release(it) }
        channel.finishAndReleaseAll()
    }
}
