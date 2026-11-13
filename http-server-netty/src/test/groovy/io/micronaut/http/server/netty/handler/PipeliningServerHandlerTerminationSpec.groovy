package io.micronaut.http.server.netty.handler

import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.body.stream.BodySizeLimits
import io.micronaut.http.body.stream.BufferConsumer
import io.micronaut.http.netty.body.NettyByteBodyFactory
import io.micronaut.http.netty.body.StreamingNettyByteBody
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class PipeliningServerHandlerTerminationSpec extends Specification {
    def 'a response with a final buffer is marked written before abort #reentrant'() {
        given:
        OutboundAccess access = null
        StreamingNettyByteBody.SharedBuffer buffer = null
        NettyByteBodyFactory factory = null
        int written = 0
        boolean abortInCallback = reentrant
        def ch = new EmbeddedChannel(new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.close()
                access = outboundAccess
                factory = new NettyByteBodyFactory(ctx.channel())
                buffer = factory.createStreamingBuffer(BodySizeLimits.UNLIMITED, new BufferConsumer.Upstream() {
                    @Override
                    void onBytesConsumed(long bytesConsumed) {
                    }
                })
                outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), new StreamingNettyByteBody(buffer))
            }

            @Override
            void responseWritten(Object attachment) {
                written++
                if (abortInCallback) {
                    access.abort()
                }
            }

            @Override
            void handleUnboundError(Throwable cause) {
                throw new AssertionError(cause)
            }
        }))

        when:
        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, '/'))
        buffer.addAndComplete(factory.readBufferFactory().copyOf('last', StandardCharsets.UTF_8))
        if (!reentrant) {
            access.abort()
        }
        ch.runPendingTasks()

        then:
        ch.checkException()
        written == 1
        ch.isOpen()

        cleanup:
        ch.finishAndReleaseAll()

        where:
        reentrant << [false, true]
    }
}
