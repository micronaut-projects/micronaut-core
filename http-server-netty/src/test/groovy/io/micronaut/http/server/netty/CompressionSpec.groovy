package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandler
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.compression.SnappyFrameDecoder
import io.netty.handler.codec.compression.ZlibCodecFactory
import io.netty.handler.codec.compression.ZlibWrapper
import io.netty.handler.codec.http.HttpHeaderValues
import spock.lang.Specification

import java.util.concurrent.ThreadLocalRandom

class CompressionSpec extends Specification {
    def compression(ChannelHandler decompressor, CharSequence contentEncoding) {
        given:
        def ctx = ApplicationContext.run(['spec.name': 'CompressionSpec'])
        def server = ctx.getBean(EmbeddedServer).start()

        byte[] uncompressed = new byte[1024]
        ThreadLocalRandom.current().nextBytes(uncompressed)
        ctx.getBean(Ctrl).data = uncompressed

        def connection = new URL("$server.URI/compress").openConnection()
        connection.addRequestProperty("Accept-Encoding", contentEncoding.toString())

        when:
        byte[] compressed = connection.inputStream.readAllBytes()
        def compChannel = new EmbeddedChannel(decompressor)
        compChannel.writeInbound(Unpooled.copiedBuffer(compressed))
        compChannel.finish()
        ByteBuf decompressed = Unpooled.buffer()
        while (true) {
            ByteBuf o = compChannel.readInbound()
            if (o == null) {
                break
            }
            decompressed.writeBytes(o)
            o.release()
        }
        then:
        ByteBufUtil.getBytes(decompressed) == uncompressed

        cleanup:
        connection.inputStream.close()
        server.stop()
        ctx.close()

        where:
        contentEncoding            | decompressor
        HttpHeaderValues.GZIP      | ZlibCodecFactory.newZlibDecoder(ZlibWrapper.GZIP)
        HttpHeaderValues.DEFLATE   | ZlibCodecFactory.newZlibDecoder(ZlibWrapper.ZLIB)
        HttpHeaderValues.SNAPPY    | new SnappyFrameDecoder()
    }

    def compressionLevel(int threshold, int actual, boolean compressed) {
        given:
        def ctx = ApplicationContext.run(['spec.name': 'CompressionSpec', 'micronaut.server.netty.compression-threshold': threshold])
        def server = ctx.getBean(EmbeddedServer).start()

        byte[] uncompressed = new byte[actual]
        ThreadLocalRandom.current().nextBytes(uncompressed)
        ctx.getBean(Ctrl).data = uncompressed

        def connection = new URL("$server.URI/compress").openConnection()
        connection.addRequestProperty("Accept-Encoding", "gzip")

        when:
        byte[] data = connection.inputStream.readAllBytes()
        then:
        connection.getHeaderField("Content-Encoding") == (compressed ? "gzip" : null)
        compressed ? (data.length != actual) : (data.length == actual)

        cleanup:
        connection.inputStream.close()
        server.stop()
        ctx.close()

        where:
        threshold | actual | compressed
        0         | 0      | false // special case
        0         | 1      | true
        1         | 0      | false
        1         | 1      | true
        -1        | 10000  | false
    }

    @Requires(property = "spec.name", value = "CompressionSpec")
    @Controller
    static class Ctrl {
        byte[] data

        @Get("/compress")
        byte[] send() {
            return data
        }
    }
}
