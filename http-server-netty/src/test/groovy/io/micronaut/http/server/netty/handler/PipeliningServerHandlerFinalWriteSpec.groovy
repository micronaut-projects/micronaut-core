package io.micronaut.http.server.netty.handler

import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.netty.body.NettyByteBodyFactory
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.HttpContent
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.LastHttpContent
import io.netty.util.ReferenceCountUtil
import reactor.core.publisher.Flux
import spock.lang.Specification

/**
 * The recorded final write of a response, see {@link OutboundAccess#recordFinalWrite()}.
 */
class PipeliningServerHandlerFinalWriteSpec extends Specification {

    def 'a final write that fails inline and closes the connection leaves the request abandoned'() {
        given:
        List<Boolean> abandonedAtClose = []
        OutboundAccess access = null
        def ch = new EmbeddedChannel(new ChannelOutboundHandlerAdapter() {
            @Override
            void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                if (msg instanceof LastHttpContent) {
                    // the write fails before write() returns
                    ReferenceCountUtil.release(msg)
                    promise.setFailure(new IOException("inline write failure"))
                } else {
                    ctx.write(msg, promise)
                }
            }
        }, new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.close()
                access = outboundAccess
                assert outboundAccess.recordFinalWrite()
                outboundAccess.whenAbandoned { abandonedAtClose.add(outboundAccess.isAbandoned()) }
                // connection: close, so that the write closes the connection when it completes
                outboundAccess.closeAfterWrite()
                outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), NettyByteBodyFactory.empty())
            }

            @Override
            void handleUnboundError(Throwable cause) {
            }
        }))

        when:
        ch.writeOneInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"))
        ch.runPendingTasks()

        then:
        !ch.isOpen()
        abandonedAtClose == [true]
        access.finalWrite() != null
        access.finalWrite().cause() instanceof IOException

        cleanup:
        ch.finishAndReleaseAll()
    }

    def 'a failed write before the final write of a recorded response is its failure'() {
        given:
        List<Throwable> failures = []
        boolean failNext = true
        def ch = new EmbeddedChannel(new ChannelOutboundHandlerAdapter() {
            @Override
            void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                if (failNext && msg instanceof HttpContent && !(msg instanceof LastHttpContent)) {
                    // the connection stays open
                    failNext = false
                    ReferenceCountUtil.release(msg)
                    promise.setFailure(new IOException("intermediate write failure"))
                } else {
                    ctx.write(msg, promise)
                }
            }
        }, new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                body.close()
                outboundAccess.attachment("request")
                assert outboundAccess.recordFinalWrite()
                // more than the 8 KiB aggregation window, so that the body is written in parts
                def content = Flux.range(0, 32).map { Unpooled.wrappedBuffer(new byte[1024]) }
                outboundAccess.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK), new NettyByteBodyFactory(ctx.channel()).adaptNetty(content))
            }

            @Override
            void responseFailed(Object attachment, Throwable cause) {
                assert attachment == "request"
                failures.add(cause)
            }

            @Override
            void handleUnboundError(Throwable cause) {
            }
        }))

        when:
        ch.writeOneInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"))
        ch.runPendingTasks()

        then:
        failures.size() == 1
        failures[0].message == "intermediate write failure"

        cleanup:
        ch.finishAndReleaseAll()
    }
}
