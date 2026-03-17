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
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.compression.SnappyFrameEncoder
import io.netty.handler.codec.compression.ZlibCodecFactory
import io.netty.handler.codec.compression.ZlibWrapper
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import spock.lang.Specification

import java.util.concurrent.ThreadLocalRandom

class RequestDecompressionConfigSpec extends Specification {

    def 'server request decompression can be disabled'(CharSequence contentEncoding, Object compressor, boolean http2) {
        given: 'an embedded server with request decompression disabled'
        Map<String, Object> cfg = [
                'spec.name'                                                : 'RequestDecompressionConfigSpec',
                'micronaut.server.netty.request-decompression-enabled'     : false,
                'micronaut.server.ssl.port'                                : 0,
                'micronaut.http.client.http-version'                       : http2 ? '2.0' : '1.1',
                'micronaut.server.http-version'                            : http2 ? '2.0' : '1.1',
                'micronaut.http.client.ssl.insecure-trust-all-certificates': true,
        ]
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, cfg)
        def client = server.applicationContext.createBean(HttpClient, server.URI).toBlocking()

        and: 'some random payload and a compressed variant we send to the server'
        byte[] uncompressed = new byte[1024]
        ThreadLocalRandom.current().nextBytes(uncompressed)

        def compChannel = (compressor instanceof ZlibWrapper)
            ? new EmbeddedChannel(ZlibCodecFactory.newZlibEncoder((ZlibWrapper) compressor))
            : new EmbeddedChannel((io.netty.channel.ChannelHandler) compressor)
        compChannel.writeOutbound(Unpooled.copiedBuffer(uncompressed))
        compChannel.finish()
        ByteBuf compressedBuf = Unpooled.buffer()
        while (true) {
            ByteBuf o = compChannel.readOutbound()
            if (o == null) {
                break
            }
            compressedBuf.writeBytes(o)
            o.release()
        }
        byte[] compressed = ByteBufUtil.getBytes(compressedBuf)

        when: 'we POST compressed data with Content-Encoding'
        client.exchange(
            HttpRequest.POST("/rdc/decompress", compressed)
                      .header(HttpHeaderNames.CONTENT_ENCODING, contentEncoding),
            byte[]
        )

        then: 'controller receives the compressed bytes unchanged (no server-side decompression)'
        server.applicationContext.getBean(Ctrl).data == compressed

        cleanup:
        client.close()
        server.stop()

        where:
        contentEncoding            | compressor               | http2
        HttpHeaderValues.GZIP      | ZlibWrapper.GZIP         | false
        HttpHeaderValues.X_GZIP    | ZlibWrapper.GZIP         | false
        // deflate can mean raw (NONE) or zlib wrapper; ensure both do not get decompressed by server
        HttpHeaderValues.DEFLATE   | ZlibWrapper.NONE         | false
        HttpHeaderValues.X_DEFLATE | ZlibWrapper.NONE         | false
        HttpHeaderValues.DEFLATE   | ZlibWrapper.ZLIB         | false
        HttpHeaderValues.X_DEFLATE | ZlibWrapper.ZLIB         | false
        HttpHeaderValues.SNAPPY    | new SnappyFrameEncoder() | false
        HttpHeaderValues.GZIP      | ZlibWrapper.GZIP         | true
        HttpHeaderValues.X_GZIP    | ZlibWrapper.GZIP         | true
        // deflate can mean raw (NONE) or zlib wrapper; ensure both do not get decompressed by server
        HttpHeaderValues.DEFLATE   | ZlibWrapper.NONE         | true
        HttpHeaderValues.X_DEFLATE | ZlibWrapper.NONE         | true
        HttpHeaderValues.DEFLATE   | ZlibWrapper.ZLIB         | true
        HttpHeaderValues.X_DEFLATE | ZlibWrapper.ZLIB         | true
        HttpHeaderValues.SNAPPY    | new SnappyFrameEncoder() | true
    }

    @Requires(property = "spec.name", value = "RequestDecompressionConfigSpec")
    @Controller("/rdc")
    static class Ctrl {
        byte[] data

        @Post("/decompress")
        void receive(@Body byte[] data) {
            this.data = data
        }
    }
}
