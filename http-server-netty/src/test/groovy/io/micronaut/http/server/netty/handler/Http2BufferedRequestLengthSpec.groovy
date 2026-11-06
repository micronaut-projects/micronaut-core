package io.micronaut.http.server.netty.handler

import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.body.InternalByteBody
import io.micronaut.http.body.stream.BodySizeLimits
import io.micronaut.http.exceptions.ContentLengthExceededException
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http2.DefaultHttp2DataFrame
import io.netty.handler.codec.http2.DefaultHttp2Headers
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame
import io.netty.handler.codec.http2.Http2ChannelDuplexHandler
import io.netty.handler.codec.http2.Http2FrameCodecBuilder
import io.netty.handler.codec.http2.Http2FrameStream
import spock.lang.Specification

/**
 * The declared Content-Length of a request is charged to the body size limit once. Request DATA
 * frames that arrive before read complete are buffered and only handed to the streaming body at
 * read complete, after the declared length is known, so they must not be charged again.
 */
class Http2BufferedRequestLengthSpec extends Specification {
    static final int MAX_BODY_SIZE = 1000

    EmbeddedChannel server
    EmbeddedChannel client
    Http2ChannelDuplexHandler duplexHandler
    final List<CloseableByteBody> accepted = []
    final List<Throwable> unboundErrors = []

    def setup() {
        server = new EmbeddedChannel(new Http2ServerHandler.ConnectionHandlerBuilder(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                accepted.add(body)
            }

            @Override
            void handleUnboundError(Throwable cause) {
                unboundErrors.add(cause)
            }
        }).bodySizeLimits(new BodySizeLimits(MAX_BODY_SIZE, MAX_BODY_SIZE)).build())
        duplexHandler = new Http2ChannelDuplexHandler() {}
        client = new EmbeddedChannel(Http2FrameCodecBuilder.forClient().build(), duplexHandler)
    }

    def cleanup() {
        accepted.each { it.close() }
        server.finishAndReleaseAll()
        client.finishAndReleaseAll()
    }

    def "the declared length is charged once when the first DATA frame arrives before read complete: Content-Length #contentLength"() {
        given: "a request whose first DATA frame is read together with the headers"
        Http2FrameStream stream = duplexHandler.newStream()
        def headers = new DefaultHttp2Headers()
        headers.method(HttpMethod.POST.asciiName())
        headers.scheme("http")
        headers.authority("example.com")
        headers.path("/")
        headers.setLong(HttpHeaderNames.CONTENT_LENGTH, contentLength)
        client.writeOutbound(new DefaultHttp2HeadersFrame(headers, false).stream(stream))
        client.writeOutbound(new DefaultHttp2DataFrame(data(firstFrame), false).stream(stream))

        when: "they are delivered without a read complete"
        deliver(false)

        then: "the DATA frame is buffered for now"
        accepted.isEmpty()

        when: "read complete hands the buffered frame to the streaming body"
        server.pipeline().fireChannelReadComplete()
        server.checkException()
        Throwable bodyError = null
        long bufferedLength = -1
        InternalByteBody.bufferFlow(accepted[0]).onComplete { buffered, error ->
            bodyError = error
            bufferedLength = buffered == null ? -1 : buffered.length()
            buffered?.close()
        }

        and: "the rest of the body arrives"
        client.writeOutbound(new DefaultHttp2DataFrame(data(contentLength - firstFrame), true).stream(stream))
        deliver(true)

        then:
        accepted.size() == 1
        unboundErrors.isEmpty()
        if (withinLimit) {
            assert bodyError == null
            assert bufferedLength == contentLength
        } else {
            assert bodyError instanceof ContentLengthExceededException
        }

        where:
        contentLength     | firstFrame | withinLimit
        MAX_BODY_SIZE - 1 | 500        | true
        MAX_BODY_SIZE     | 500        | true
        MAX_BODY_SIZE + 1 | 500        | false
    }

    /**
     * Deliver everything the client wrote to the server as a single read, with or without the
     * read complete.
     */
    private void deliver(boolean readComplete) {
        ByteBuf read = Unpooled.buffer()
        ByteBuf msg
        while ((msg = client.readOutbound()) != null) {
            read.writeBytes(msg)
            msg.release()
        }
        server.writeOneInbound(read)
        if (readComplete) {
            server.pipeline().fireChannelReadComplete()
        }
        server.checkException()
    }

    private static ByteBuf data(int size) {
        ByteBuf buf = ByteBufAllocator.DEFAULT.buffer(size)
        buf.writeZero(size)
        return buf
    }
}
