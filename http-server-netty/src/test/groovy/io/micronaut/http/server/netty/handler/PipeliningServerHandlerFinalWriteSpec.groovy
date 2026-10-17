package io.micronaut.http.server.netty.handler

import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.netty.body.NettyByteBodyFactory
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.LastHttpContent
import io.netty.util.ReferenceCountUtil
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
}
