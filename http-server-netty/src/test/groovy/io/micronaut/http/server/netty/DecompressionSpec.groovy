package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandler
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.compression.SnappyFrameEncoder
import io.netty.handler.codec.compression.ZlibCodecFactory
import io.netty.handler.codec.compression.ZlibWrapper
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import spock.lang.Specification

import java.util.concurrent.ThreadLocalRandom

class DecompressionSpec extends Specification {
    def decompression(ChannelHandler compressor, CharSequence contentEncoding) {
        given:
        def ctx = ApplicationContext.run(['spec.name': 'DecompressionSpec'])
        def server = ctx.getBean(EmbeddedServer).start()
        def client = ctx.createBean(HttpClient, server.URI).toBlocking()

        def compChannel = new EmbeddedChannel(compressor)
        byte[] uncompressed = new byte[1024]
        ThreadLocalRandom.current().nextBytes(uncompressed)
        compChannel.writeOutbound(Unpooled.copiedBuffer(uncompressed))
        compChannel.finish()
        ByteBuf compressed = Unpooled.buffer()
        while (true) {
            ByteBuf o = compChannel.readOutbound()
            if (o == null) {
                break
            }
            compressed.writeBytes(o)
            o.release()
        }

        when:
        client.exchange(HttpRequest.POST("/decompress", ByteBufUtil.getBytes(compressed)).header(HttpHeaderNames.CONTENT_ENCODING, contentEncoding))
        then:
        ctx.getBean(Ctrl).data == uncompressed

        cleanup:
        client.close()
        server.stop()
        ctx.close()

        where:
        contentEncoding            | compressor
        HttpHeaderValues.GZIP      | ZlibCodecFactory.newZlibEncoder(ZlibWrapper.GZIP)
        HttpHeaderValues.X_GZIP    | ZlibCodecFactory.newZlibEncoder(ZlibWrapper.GZIP)
        HttpHeaderValues.DEFLATE   | ZlibCodecFactory.newZlibEncoder(ZlibWrapper.NONE)
        HttpHeaderValues.X_DEFLATE | ZlibCodecFactory.newZlibEncoder(ZlibWrapper.NONE)
        HttpHeaderValues.SNAPPY    | new SnappyFrameEncoder()
    }

    @Requires(property = "spec.name", value = "DecompressionSpec")
    @Controller
    static class Ctrl {
        byte[] data

        @Post("/decompress")
        void receive(@Body byte[] data) {
            this.data = data
        }
    }
}
