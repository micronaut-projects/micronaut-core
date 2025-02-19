package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.core.annotation.NonNull
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Produces
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.netty.NettyClientCustomizer
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandler
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.compression.SnappyFrameDecoder
import io.netty.handler.codec.compression.SnappyFrameEncoder
import io.netty.handler.codec.compression.ZlibCodecFactory
import io.netty.handler.codec.compression.ZlibWrapper
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import jakarta.inject.Singleton
import spock.lang.Specification

import java.util.concurrent.ThreadLocalRandom

class CompressionSpec extends Specification {
    protected Map<String, Object> serverOptions() {
        return [:]
    }

    def compression(ChannelHandler decompressor, CharSequence contentEncoding) {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'CompressionSpec'] + serverOptions())

        byte[] uncompressed = new byte[1024]
        ThreadLocalRandom.current().nextBytes(uncompressed)
        server.applicationContext.getBean(Ctrl).data = uncompressed

        def client = server.applicationContext.createBean(HttpClient, server.URI).toBlocking()

        when:
        byte[] compressed = client.retrieve(HttpRequest.GET("/compress").header("Accept-Encoding", contentEncoding.toString()), byte[])
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
        client.close()
        server.stop()

        where:
        contentEncoding            | decompressor
        HttpHeaderValues.GZIP      | ZlibCodecFactory.newZlibDecoder(ZlibWrapper.GZIP)
        HttpHeaderValues.DEFLATE   | ZlibCodecFactory.newZlibDecoder(ZlibWrapper.ZLIB)
        HttpHeaderValues.SNAPPY    | new SnappyFrameDecoder()
    }

    def compressionLevel(int threshold, int actual, boolean compressed) {
        given:
        def server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'CompressionSpec', 'micronaut.server.netty.compression-threshold': threshold] + serverOptions())

        byte[] uncompressed = new byte[actual]
        ThreadLocalRandom.current().nextBytes(uncompressed)
        server.applicationContext.getBean(Ctrl).data = uncompressed

        def client = server.applicationContext.createBean(HttpClient, server.URI).toBlocking()

        when:
        def response = client.exchange(HttpRequest.GET("/compress").header("Accept-Encoding", "gzip"), byte[])
        byte[] data = response.body() == null ? new byte[0] : response.body()
        then:
        response.header("Content-Encoding") == (compressed ? "gzip" : null)
        compressed ? (data.length != actual) : (data.length == actual)

        cleanup:
        client.close()
        server.stop()

        where:
        threshold | actual | compressed
        0         | 0      | false // special case
        0         | 1      | true
        1         | 0      | false
        1         | 1      | true
        -1        | 10000  | false
    }

    def decompression(ChannelHandler compressor, CharSequence contentEncoding) {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'CompressionSpec'] + serverOptions())
        def client = server.applicationContext.createBean(HttpClient, server.URI).toBlocking()

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
        server.applicationContext.getBean(Ctrl).data == uncompressed

        cleanup:
        client.close()
        server.stop()

        where:
        contentEncoding            | compressor
        HttpHeaderValues.GZIP      | ZlibCodecFactory.newZlibEncoder(ZlibWrapper.GZIP)
        HttpHeaderValues.X_GZIP    | ZlibCodecFactory.newZlibEncoder(ZlibWrapper.GZIP)
        HttpHeaderValues.DEFLATE   | ZlibCodecFactory.newZlibEncoder(ZlibWrapper.NONE)
        HttpHeaderValues.X_DEFLATE | ZlibCodecFactory.newZlibEncoder(ZlibWrapper.NONE)
        HttpHeaderValues.DEFLATE   | ZlibCodecFactory.newZlibEncoder(ZlibWrapper.ZLIB)
        HttpHeaderValues.X_DEFLATE | ZlibCodecFactory.newZlibEncoder(ZlibWrapper.ZLIB)
        HttpHeaderValues.SNAPPY    | new SnappyFrameEncoder()
    }

    def compressionStream(ChannelHandler decompressor, CharSequence contentEncoding) {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'CompressionSpec'] + serverOptions())

        byte[] uncompressed = new byte[100000]
        ThreadLocalRandom.current().nextBytes(uncompressed)
        server.applicationContext.getBean(Ctrl).data = uncompressed

        def client = server.applicationContext.createBean(HttpClient, server.URI).toBlocking()

        when:
        byte[] compressed = client.retrieve(HttpRequest.GET("/compress-stream").header("Accept-Encoding", contentEncoding.toString()), byte[])
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
        client.close()
        server.stop()

        where:
        contentEncoding            | decompressor
        HttpHeaderValues.GZIP      | ZlibCodecFactory.newZlibDecoder(ZlibWrapper.GZIP)
        HttpHeaderValues.DEFLATE   | ZlibCodecFactory.newZlibDecoder(ZlibWrapper.ZLIB)
        HttpHeaderValues.SNAPPY    | new SnappyFrameDecoder()
    }

    @Requires(property = "spec.name", value = "CompressionSpec")
    @Controller
    static class Ctrl {
        byte[] data

        @Get("/compress")
        byte[] send() {
            return data
        }

        @Post("/decompress")
        void receive(@Body byte[] data) {
            this.data = data
        }

        @Get("/compress-stream")
        @Produces("text/plain")
        InputStream sendStream() {
            return new ByteArrayInputStream(data)
        }
    }

    // the code below disables the automatic decompression in the http client so that we can see the compressed data in the test

    @Requires(property = "spec.name", value = "CompressionSpec")
    @Singleton
    static class ClientPatch implements BeanCreatedEventListener<NettyClientCustomizer.Registry> {
        @Override
        NettyClientCustomizer.Registry onCreated(@NonNull BeanCreatedEvent<NettyClientCustomizer.Registry> event) {
            event.bean.register(new ClientCustomizer(null))
            return event.bean
        }
    }

    private static class ClientCustomizer implements NettyClientCustomizer {
        final Channel channel

        ClientCustomizer(Channel channel) {
            this.channel = channel
        }

        @Override
        NettyClientCustomizer specializeForChannel(@NonNull Channel channel, @NonNull ChannelRole role) {
            return new ClientCustomizer(channel)
        }

        @Override
        void onRequestPipelineBuilt() {
            try {
                channel.pipeline().remove(ChannelPipelineCustomizer.HANDLER_HTTP_DECODER)
            } catch (NoSuchElementException ignored) {
            }
            try {
                channel.pipeline().remove(ChannelPipelineCustomizer.HANDLER_HTTP_DECOMPRESSOR)
            } catch (NoSuchElementException ignored) {
            }
        }
    }
}
