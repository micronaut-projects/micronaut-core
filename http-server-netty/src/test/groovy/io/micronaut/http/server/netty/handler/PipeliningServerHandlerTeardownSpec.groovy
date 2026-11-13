package io.micronaut.http.server.netty.handler

import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.body.stream.BodySizeLimits
import io.micronaut.http.body.stream.BufferConsumer
import io.micronaut.http.netty.body.NettyByteBodyFactory
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.LastHttpContent
import reactor.core.publisher.Sinks
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class PipeliningServerHandlerTeardownSpec extends Specification {

    def 'abort does nothing after a streaming response ended with its last bytes'() {
        given:
        def resp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
        resp.headers().add(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
        OutboundAccess access = null
        NettyByteBodyFactory bodyFactory = null
        def streamingBody = null
        int written = 0
        def ch = new EmbeddedChannel(new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.close()
                access = outboundAccess
                bodyFactory = new NettyByteBodyFactory(ctx.channel())
                streamingBody = bodyFactory.createStreamingBody(BodySizeLimits.UNLIMITED, new BufferConsumer.Upstream() {
                    @Override
                    void onBytesConsumed(long bytesConsumed) {
                        // the body is written by the test itself, so there is no source to ask for more
                    }
                })
                outboundAccess.write(resp, streamingBody.rootBody())
            }

            @Override
            void responseWritten(Object attachment) {
                written++
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        }))

        when: 'the last bytes of the body arrive together with its end'
        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"))
        streamingBody.sharedBuffer().addAndComplete(bodyFactory.readBufferFactory().copyOf("foo", StandardCharsets.UTF_8))
        then:
        written == 1
        ch.readOutbound() == resp
        LastHttpContent last = ch.readOutbound()
        last.content().toString(StandardCharsets.UTF_8) == "foo"

        when: 'the request handler aborts, e.g. because something failed after the response ended'
        access.abort()
        then: 'the response is complete, so the connection stays'
        ch.isOpen()
        written == 1

        cleanup:
        last?.release()
        ch.finishAndReleaseAll()
    }

    def 'a response whose cleanup fails does not keep the other responses from being cleaned up when the connection closes'() {
        given:
        def resp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
        resp.headers().add(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
        List<Object> cleaned = []
        List<Integer> cancelled = []
        boolean removed = false
        def ch = new EmbeddedChannel(new PipeliningServerHandler(new RequestHandler() {
            int i = 0

            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.close()
                int n = i++
                outboundAccess.attachment(n)
                // never completes: both responses are still open when the connection closes
                def responseBody = Sinks.many().unicast().<ByteBuf>onBackpressureBuffer().asFlux().doOnCancel { cancelled.add(n) }
                outboundAccess.write(resp, new NettyByteBodyFactory(ctx.channel()).adaptNetty(responseBody))
            }

            @Override
            void responseWritten(Object attachment) {
                cleaned.add(attachment)
                if (attachment == 0) {
                    throw new IllegalStateException("cleanup of the first request failed")
                }
            }

            @Override
            void removed() {
                removed = true
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        }))

        when:
        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"))
        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"))
        ch.close()
        then:
        cleaned == [0, 1]
        // the body of the response whose cleanup failed is cancelled too. The queued response
        // never subscribed to its body, so there is nothing to cancel for it
        cancelled == [0]
        removed

        cleanup:
        ch.releaseOutbound()
    }
}
