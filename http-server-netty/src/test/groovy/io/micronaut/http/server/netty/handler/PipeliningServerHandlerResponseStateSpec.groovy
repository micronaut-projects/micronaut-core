package io.micronaut.http.server.netty.handler

import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.body.stream.BodySizeLimits
import io.micronaut.http.netty.body.NettyByteBodyFactory
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.DefaultHttpRequest
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpContent
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import reactor.core.publisher.Flux
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class PipeliningServerHandlerResponseStateSpec extends Specification {
    def 'the final piece of a streamed response that the connection closed under while writing an earlier piece is released'() {
        given:
        def resp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
        resp.headers().add(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
        def streamingBody = null
        List<Object> written = []
        def ch = new EmbeddedChannel(new ChannelOutboundHandlerAdapter() {
            boolean closed

            @Override
            void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                super.write(ctx, msg, promise)
                if (msg instanceof HttpContent && !closed) {
                    // the connection closes while the first piece is written, which removes
                    // the handler and discards the response
                    closed = true
                    ctx.channel().close()
                }
            }
        }, new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.close()
                outboundAccess.attachment(request.uri())
                streamingBody = new NettyByteBodyFactory(ctx.channel()).createStreamingBody(BodySizeLimits.UNLIMITED, new PipeliningServerHandlerSpec.RecordingUpstream())
                outboundAccess.write(resp, streamingBody.rootBody())
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }

            @Override
            void responseWritten(Object attachment) {
                written << attachment
            }
        }))
        def readBuffers = new NettyByteBodyFactory(ch).readBufferFactory()
        def last = Unpooled.buffer(2048).writeBytes(new byte[2048])

        when:
        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/only"))
        // a small piece and the final piece arrive in the same turn
        streamingBody.sharedBuffer().add(readBuffers.adapt(Unpooled.copiedBuffer("a", StandardCharsets.UTF_8)))
        streamingBody.sharedBuffer().addAndComplete(readBuffers.adapt(last))
        ch.runPendingTasks()

        then:
        noExceptionThrown()
        !ch.isOpen()
        last.refCnt() == 0
        written == ['/only']

        cleanup:
        ch.finishAndReleaseAll()
    }

    def 'a 100 Continue requested behind an earlier response is written before its response, after the earlier one'() {
        given:
        List<OutboundAccess> accesses = []
        List<Object> written = []
        def ch = new EmbeddedChannel(new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                outboundAccess.attachment(request.uri())
                accesses << outboundAccess
                if (request.uri() == '/second') {
                    // demanding the body requests the 100 Continue, while the first response is
                    // still to be written
                    Flux.from(body.toByteArrayPublisher()).subscribe()
                } else {
                    body.close()
                }
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }

            @Override
            void responseWritten(Object attachment) {
                written << attachment
            }
        }))

        when:
        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/first"))
        def second = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/second")
        second.headers().add(HttpHeaderNames.EXPECT, HttpHeaderValues.CONTINUE)
        second.headers().add(HttpHeaderNames.CONTENT_LENGTH, 3)
        ch.writeInbound(second)
        ch.runPendingTasks()

        then:
        accesses.size() == 2
        ch.readOutbound() == null

        when:
        // the response follows the queued 100 Continue
        accesses[1].write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.ACCEPTED), NettyByteBodyFactory.empty())
        ch.runPendingTasks()

        then:
        ch.readOutbound() == null
        written.isEmpty()

        when:
        accesses[0].write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NO_CONTENT), NettyByteBodyFactory.empty())
        ch.runPendingTasks()

        then:
        (ch.readOutbound() as FullHttpResponse).status() == HttpResponseStatus.NO_CONTENT
        (ch.readOutbound() as FullHttpResponse).status() == HttpResponseStatus.CONTINUE
        (ch.readOutbound() as FullHttpResponse).status() == HttpResponseStatus.ACCEPTED
        ch.readOutbound() == null
        written == ['/first', '/second']

        cleanup:
        ch.finishAndReleaseAll()
    }

    def 'a response written after the handler was removed is released and reported once'() {
        given:
        OutboundAccess access = null
        List<Object> written = []
        def handler = new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.close()
                outboundAccess.attachment(request.uri())
                access = outboundAccess
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }

            @Override
            void responseWritten(Object attachment) {
                written << attachment
            }
        })
        def ch = new EmbeddedChannel(handler)

        when:
        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/only"))
        ch.pipeline().remove(handler)
        def content = Unpooled.copiedBuffer("foo", StandardCharsets.UTF_8)
        access.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK),
            new NettyByteBodyFactory(ch).createChecked(BodySizeLimits.UNLIMITED, content))
        ch.runPendingTasks()

        then:
        content.refCnt() == 0
        ch.readOutbound() == null
        written == ['/only']

        when:
        // an abort after the removal does nothing
        access.abort()
        ch.runPendingTasks()

        then:
        ch.isOpen()
        written == ['/only']

        cleanup:
        ch.finishAndReleaseAll()
    }
}
