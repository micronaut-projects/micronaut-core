package io.micronaut.http.server.netty.handler

import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.body.InternalByteBody
import io.micronaut.http.body.stream.BodySizeLimits
import io.micronaut.http.exceptions.ContentLengthExceededException
import io.micronaut.http.netty.body.NettyByteBodyFactory
import io.micronaut.http.server.netty.EmbeddedTestUtil
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http2.DefaultHttp2DataFrame
import io.netty.handler.codec.http2.DefaultHttp2Headers
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame
import io.netty.handler.codec.http2.Http2ChannelDuplexHandler
import io.netty.handler.codec.http2.Http2Error
import io.netty.handler.codec.http2.Http2FrameCodecBuilder
import io.netty.handler.codec.http2.Http2FrameStream
import io.netty.handler.codec.http2.Http2HeadersFrame
import io.netty.handler.codec.http2.Http2ResetFrame
import io.netty.handler.codec.http2.Http2SettingsAckFrame
import io.netty.handler.codec.http2.Http2SettingsFrame
import io.netty.util.AsciiString
import spock.lang.Issue
import spock.lang.Specification

/**
 * A request whose declared Content-Length is over the body size limit is rejected as soon as the
 * body is set up. On HTTP/2 that must not leave the stream open: once the error response is
 * written, the server resets the stream so the client stops sending the rest of the body.
 */
class Http2RejectedBodySpec extends Specification {

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/13243')
    def "an oversized Content-Length is answered and the stream reset"() {
        given: "a handler that buffers the body and answers 413 when that fails"
        List<Throwable> bodyErrors = []
        EmbeddedChannel server = new EmbeddedChannel()
        EmbeddedChannel client = new EmbeddedChannel()
        EmbeddedTestUtil.connect(server, client)
        server.pipeline().addLast(new Http2ServerHandler.ConnectionHandlerBuilder(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                InternalByteBody.bufferFlow(body).onComplete { buffered, error ->
                    bodyErrors.add(error)
                    buffered?.close()
                    outboundAccess.write(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, error == null ? HttpResponseStatus.OK : HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE), NettyByteBodyFactory.empty())
                }
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        }).bodySizeLimits(new BodySizeLimits(1000, 1000)).build())
        def duplexHandler = new Http2ChannelDuplexHandler() {}
        client.pipeline().addLast(Http2FrameCodecBuilder.forClient().build(), duplexHandler)

        when: "the client declares a body over the limit and starts sending it"
        Http2FrameStream stream = duplexHandler.newStream()
        def headers = new DefaultHttp2Headers()
        headers.method(HttpMethod.POST.asciiName())
        headers.scheme("http")
        headers.authority("example.com")
        headers.path("/")
        headers.setLong(HttpHeaderNames.CONTENT_LENGTH, 100_000)
        client.writeOutbound(new DefaultHttp2HeadersFrame(headers, false).stream(stream))
        client.writeOutbound(new DefaultHttp2DataFrame(data(500), false).stream(stream))
        EmbeddedTestUtil.advance(server, client)

        then: "the body is rejected from its declared length"
        bodyErrors.size() == 1
        bodyErrors[0] instanceof ContentLengthExceededException

        and: "the 413 is followed by a reset that does not signal an error, so the client stops uploading"
        client.readInbound() instanceof Http2SettingsFrame
        client.readInbound() instanceof Http2SettingsAckFrame
        Http2HeadersFrame response = client.readInbound()
        AsciiString.contentEquals(response.headers().status(), HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE.codeAsText())
        response.isEndStream()
        def rst = client.readInbound()
        rst instanceof Http2ResetFrame
        rst.stream() == stream
        rst.errorCode() == Http2Error.NO_ERROR.code()

        cleanup:
        client.checkException()
        server.checkException()
        client.finishAndReleaseAll()
        server.finishAndReleaseAll()
        EmbeddedTestUtil.advance(client, server)
    }

    private static ByteBuf data(int size) {
        ByteBuf buf = ByteBufAllocator.DEFAULT.buffer(size)
        buf.writeZero(size)
        return buf
    }
}
