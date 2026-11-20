package io.micronaut.http.server.netty.handler

import io.micronaut.http.body.CloseableByteBody
import io.netty.buffer.ByteBuf
import io.netty.buffer.CompositeByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.compression.SnappyFrameEncoder
import io.netty.handler.codec.compression.ZlibCodecFactory
import io.netty.handler.codec.compression.ZlibWrapper
import io.netty.handler.codec.http.DefaultHttpContent
import io.netty.handler.codec.http.DefaultHttpRequest
import io.netty.handler.codec.http.DefaultLastHttpContent
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpVersion
import reactor.core.publisher.Flux
import spock.lang.Specification

import java.util.concurrent.ThreadLocalRandom

class RequestDecompressionSpec extends Specification {

    def 'compressed streaming request respects read backpressure: #contentEncoding'(ChannelHandler compressor, CharSequence contentEncoding) {
        given:
        def mon = new PipeliningServerHandlerSpec.MonitorHandler()
        CloseableByteBody sbb = null
        def ch = new EmbeddedChannel(mon, new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                sbb = body
            }

            @Override
            void handleUnboundError(Throwable cause) {
                cause.printStackTrace()
            }
        }))
        ByteBuf compressed = compress(compressor, 1024 * 1024)

        when:
        def req = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/")
        if (contentEncoding != null) {
            req.headers().set(HttpHeaderNames.CONTENT_ENCODING, contentEncoding)
        }
        req.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
        ch.writeInbound(req)
        then:
        mon.read == 2

        when: 'the content inflates to more than the 64K accepted before downstream demand'
        ch.writeInbound(new DefaultHttpContent(compressed))
        then: 'no further read until the body is consumed'
        mon.read == 2

        cleanup:
        sbb?.close()
        ch.finishAndReleaseAll()

        where:
        contentEncoding          | compressor
        null                     | null
        HttpHeaderValues.GZIP    | ZlibCodecFactory.newZlibEncoder(ZlibWrapper.GZIP)
        HttpHeaderValues.DEFLATE | ZlibCodecFactory.newZlibEncoder(ZlibWrapper.NONE)
        HttpHeaderValues.SNAPPY  | new SnappyFrameEncoder()
    }

    def 'compressed request that ends before its compressed stream does not complete: #contentEncoding missing #missing'(CharSequence contentEncoding, ZlibWrapper wrapper, int missing) {
        given:
        Throwable failure = null
        boolean completed = false
        def handler = new PipeliningServerHandler(new RequestHandler() {
            @Override
            void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
                Flux.from(body.toByteArrayPublisher()).subscribe({ }, { failure = it }, { completed = true })
            }

            @Override
            void handleUnboundError(Throwable cause) {
                failure = cause
            }
        })
        def ch = new EmbeddedChannel(handler)
        byte[] content = new byte[1000]
        ThreadLocalRandom.current().nextBytes(content)
        def enc = new EmbeddedChannel(ZlibCodecFactory.newZlibEncoder(wrapper))
        enc.writeOutbound(Unpooled.wrappedBuffer(content))
        enc.finish()
        ByteBuf compressed = Unpooled.buffer()
        ByteBuf o
        while ((o = enc.readOutbound()) != null) {
            compressed.writeBytes(o)
            o.release()
        }
        if (missing > 0) {
            compressed.writerIndex(compressed.writerIndex() - missing)
        }

        when:
        def requestMessage = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/truncated")
        requestMessage.headers().set(HttpHeaderNames.CONTENT_ENCODING, contentEncoding)
        requestMessage.headers().set(HttpHeaderNames.CONTENT_LENGTH, compressed.readableBytes())
        ch.writeOneInbound(requestMessage)
        ch.writeOneInbound(new DefaultLastHttpContent(compressed))
        ch.flushInbound()

        then:
        completed == (missing == 0)
        (failure != null) == (missing != 0)

        cleanup:
        ch.finishAndReleaseAll()

        where:
        contentEncoding          | wrapper          | missing
        HttpHeaderValues.GZIP    | ZlibWrapper.GZIP | 0
        HttpHeaderValues.GZIP    | ZlibWrapper.GZIP | 500
        HttpHeaderValues.GZIP    | ZlibWrapper.GZIP | 8
        HttpHeaderValues.DEFLATE | ZlibWrapper.ZLIB | 0
        HttpHeaderValues.DEFLATE | ZlibWrapper.ZLIB | 500
    }

    private static ByteBuf compress(ChannelHandler compressor, int size) {
        if (compressor == null) {
            return Unpooled.wrappedBuffer(new byte[size])
        }
        def compChannel = new EmbeddedChannel(compressor)
        compChannel.writeOutbound(Unpooled.wrappedBuffer(new byte[size]))
        compChannel.finish()
        CompositeByteBuf compressed = Unpooled.compositeBuffer()
        while (true) {
            ByteBuf o = compChannel.readOutbound()
            if (o == null) {
                break
            }
            compressed.addComponent(true, o)
        }
        return compressed
    }
}
