package io.micronaut.http.server.netty.handler

import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.netty.body.NettyByteBodyFactory
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import reactor.core.publisher.Flux
import spock.lang.Specification

class CanHaveBodySpec extends Specification {

    def 'informational statuses cannot have a body'() {
        expect:
        (100..199).every { !PipeliningServerHandler.canHaveBody(HttpResponseStatus.valueOf(it)) }
        (100..199).every { !PipeliningServerHandler.canHaveBody(new HttpResponseStatus(it, "Custom")) }
        !PipeliningServerHandler.canHaveBody(HttpResponseStatus.EARLY_HINTS)
    }

    def 'status #status cannot have a body'(HttpResponseStatus status) {
        expect:
        !PipeliningServerHandler.canHaveBody(status)

        where:
        status << [
                HttpResponseStatus.NO_CONTENT,
                HttpResponseStatus.NOT_MODIFIED,
                new HttpResponseStatus(204, "No Content"),
                new HttpResponseStatus(304, "Not Modified"),
                new HttpResponseStatus(204, "Nothing Here"),
                HttpResponseStatus.valueOf(204, "Nothing Here"),
        ]
    }

    def 'status #status can have a body'(HttpResponseStatus status) {
        expect:
        PipeliningServerHandler.canHaveBody(status)

        where:
        status << [
                HttpResponseStatus.OK,
                HttpResponseStatus.NOT_FOUND,
                new HttpResponseStatus(200, "Fine"),
                new HttpResponseStatus(404, "Gone Fishing"),
                HttpResponseStatus.RESET_CONTENT,
        ]
    }

    def 'noncanonical #status full response has no framing headers and keeps the connection open'(HttpResponseStatus status) {
        given:
        def ch = new EmbeddedChannel(new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.close()
                outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, status), NettyByteBodyFactory.empty())
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        }))

        when:
        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"))
        HttpResponse response = ch.readOutbound()

        then:
        response.status().code() == status.code()
        !response.headers().contains(HttpHeaderNames.CONTENT_LENGTH)
        !response.headers().contains(HttpHeaderNames.TRANSFER_ENCODING)
        !response.headers().contains(HttpHeaderNames.CONNECTION)
        ch.isOpen()
        ch.checkException()

        cleanup:
        ch.finishAndReleaseAll()

        where:
        status << [new HttpResponseStatus(204, "Nothing Here"), new HttpResponseStatus(304, "Unchanged")]
    }

    def 'noncanonical #status streaming response has no framing headers and keeps the connection open'(HttpResponseStatus status) {
        given:
        def ch = new EmbeddedChannel(new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.close()
                outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, status),
                        new NettyByteBodyFactory(ctx.channel()).adaptNetty(Flux.<ByteBuf> empty()))
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        }))

        when:
        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"))
        ch.runPendingTasks()
        HttpResponse response = ch.readOutbound()

        then:
        response.status().code() == status.code()
        !response.headers().contains(HttpHeaderNames.CONTENT_LENGTH)
        !response.headers().contains(HttpHeaderNames.TRANSFER_ENCODING)
        !response.headers().contains(HttpHeaderNames.CONNECTION)
        ch.isOpen()
        ch.checkException()

        cleanup:
        ch.finishAndReleaseAll()

        where:
        status << [new HttpResponseStatus(204, "Nothing Here"), new HttpResponseStatus(304, "Unchanged")]
    }
}
