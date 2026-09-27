package io.micronaut.http.server.netty.handler

import io.micronaut.http.body.CloseableByteBody
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http2.DefaultHttp2DataFrame
import io.netty.handler.codec.http2.DefaultHttp2Headers
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame
import io.netty.handler.codec.http2.DefaultHttp2ResetFrame
import io.netty.handler.codec.http2.Http2ChannelDuplexHandler
import io.netty.handler.codec.http2.Http2Error
import io.netty.handler.codec.http2.Http2FrameCodecBuilder
import io.netty.handler.codec.http2.Http2FrameStream
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture

/**
 * A request body that is already streaming when its stream closes must be failed, so that its
 * consumer stops waiting and releases the data it holds.
 */
class Http2StreamingRequestBodySpec extends Specification {
    EmbeddedChannel server
    EmbeddedChannel client
    Http2ChannelDuplexHandler duplexHandler
    final List<CloseableByteBody> accepted = []

    def setup() {
        def connectionHandler = new Http2ServerHandler.ConnectionHandlerBuilder(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                accepted.add(body)
            }

            @Override
            void handleUnboundError(Throwable cause) {
            }
        }).build()
        server = new EmbeddedChannel(connectionHandler)
        duplexHandler = new Http2ChannelDuplexHandler() {}
        client = new EmbeddedChannel(Http2FrameCodecBuilder.forClient().build(), duplexHandler)
    }

    def cleanup() {
        accepted.each { it.close() }
        server.finishAndReleaseAll()
        client.finishAndReleaseAll()
    }

    private Http2FrameStream writeRequestStart() {
        def stream = duplexHandler.newStream()
        def headers = new DefaultHttp2Headers()
        headers.method(HttpMethod.POST.asciiName())
        headers.scheme("http")
        headers.authority("example.com")
        headers.path("/")
        client.writeOutbound(new DefaultHttp2HeadersFrame(headers, false).stream(stream))
        client.writeOutbound(new DefaultHttp2DataFrame(Unpooled.copiedBuffer("foo", StandardCharsets.UTF_8), false).stream(stream))
        return stream
    }

    private ByteBuf deliverAsOneRead() {
        ByteBuf read = Unpooled.buffer()
        ByteBuf msg
        while ((msg = client.readOutbound()) != null) {
            read.writeBytes(msg)
            msg.release()
        }
        server.writeOneInbound(read)
        server.pipeline().fireChannelReadComplete()
        server.checkException()
        return read
    }

    def "a streaming request body is failed when the connection is lost"() {
        given: 'a request whose body is being buffered by the application'
        writeRequestStart()
        def read = deliverAsOneRead()
        CompletableFuture<?> buffered = accepted.remove(0).buffer()

        expect:
        !buffered.isDone()
        read.refCnt() > 0

        when:
        loseConnection(server)
        server.checkException()

        then:
        buffered.isCompletedExceptionally()
        read.refCnt() == 0
    }

    def "a streaming request body is failed when the client resets the stream"() {
        given:
        def stream = writeRequestStart()
        def read = deliverAsOneRead()
        CompletableFuture<?> buffered = accepted.remove(0).buffer()

        when:
        client.writeOutbound(new DefaultHttp2ResetFrame(Http2Error.CANCEL).stream(stream))
        deliverAsOneRead()

        then:
        buffered.isCompletedExceptionally()
        read.refCnt() == 0
    }

    private static void loseConnection(EmbeddedChannel ch) {
        ch.unsafe().close(ch.unsafe().voidPromise())
        ch.runPendingTasks()
    }
}
