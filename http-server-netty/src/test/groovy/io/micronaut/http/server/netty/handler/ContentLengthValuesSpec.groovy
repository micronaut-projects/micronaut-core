package io.micronaut.http.server.netty.handler

import io.micronaut.http.body.CloseableByteBody
import io.micronaut.http.netty.body.NettyByteBodyFactory
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.DefaultHttpHeaders
import io.netty.handler.codec.http.DefaultHttpResponse
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponseEncoder
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpUtil
import io.netty.handler.codec.http.HttpVersion
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class ContentLengthValuesSpec extends Specification {

    def 'value is the decimal length'(long length) {
        expect:
        ContentLengthValues.of(length).toString() == Long.toString(length)
        ContentLengthValues.of(length).length() == Long.toString(length).length()

        where:
        length << [0, 1, 9, 10, 13, 99, 100, 1023, 1024, 1025, 65536, 1_000_000, Integer.MAX_VALUE, Integer.MAX_VALUE + 1L, Long.MAX_VALUE]
    }

    def 'small values are cached, large values are not'() {
        expect:
        ContentLengthValues.of(0).is(ContentLengthValues.of(0))
        ContentLengthValues.of(ContentLengthValues.CACHE_SIZE - 1).is(ContentLengthValues.of(ContentLengthValues.CACHE_SIZE - 1))
        !ContentLengthValues.of(ContentLengthValues.CACHE_SIZE).is(ContentLengthValues.of(ContentLengthValues.CACHE_SIZE))
    }

    def 'set replaces the existing value and reads back like a number'() {
        given:
        def headers = new DefaultHttpHeaders()
        headers.add(HttpHeaderNames.CONTENT_LENGTH, "5")
        headers.add(HttpHeaderNames.CONTENT_LENGTH, "6")

        when:
        ContentLengthValues.set(headers, 123456)

        then:
        headers.getAll(HttpHeaderNames.CONTENT_LENGTH) == ["123456"]
        headers.get("Content-Length") == "123456"
        headers.getInt(HttpHeaderNames.CONTENT_LENGTH) == 123456
        headers.contains(HttpHeaderNames.CONTENT_LENGTH, "123456", false)
        HttpUtil.getContentLength(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, headers)) == 123456L
    }

    def 'full response has the content length on the wire'(int length) {
        given:
        byte[] body = new byte[length]
        Arrays.fill(body, (byte) 'a')
        def ch = new EmbeddedChannel(new HttpResponseEncoder(), new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody b, OutboundAccess outboundAccess) {
                b.close()
                def response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
                if (request.method() == HttpMethod.HEAD) {
                    response.headers().set(HttpHeaderNames.CONTENT_LENGTH, "42")
                    outboundAccess.writeHeadResponse(response)
                } else {
                    outboundAccess.write(response, new NettyByteBodyFactory(ctx.channel()).adapt(Unpooled.wrappedBuffer(body)))
                }
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        }))

        when:
        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"))
        ch.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.HEAD, "/"))
        def wire = readAll(ch)

        then:
        wire.startsWith("HTTP/1.1 200 OK\r\ncontent-length: " + length + "\r\n\r\n")
        wire.indexOf("HTTP/1.1 200 OK\r\ncontent-length: 42\r\n\r\n") == "HTTP/1.1 200 OK\r\ncontent-length: ".length() + Integer.toString(length).length() + 4 + length

        cleanup:
        ch.finishAndReleaseAll()

        where:
        length << [0, 13, 1023, 1024, 100_000]
    }

    private static String readAll(EmbeddedChannel ch) {
        def sb = new StringBuilder()
        Object o
        while ((o = ch.readOutbound()) != null) {
            ByteBuf buf = (ByteBuf) o
            sb.append(buf.toString(StandardCharsets.US_ASCII))
            buf.release()
        }
        return sb.toString()
    }
}
