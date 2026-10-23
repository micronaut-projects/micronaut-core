package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.HttpClient
import io.micronaut.http.netty.compression.NettyZlibCodecFactory
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.channel.ChannelHandler
import io.netty.handler.codec.compression.ZlibCodecFactory
import io.netty.handler.codec.compression.ZlibDecoder
import io.netty.handler.codec.compression.ZlibEncoder
import io.netty.handler.codec.compression.ZlibWrapper
import jakarta.inject.Singleton
import spock.lang.Specification

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPOutputStream

class ZlibCodecFactorySpec extends Specification {
    def 'replacement handles #encoding request and response streams for #protocol'(String protocol, String encoding) {
        given:
        Map options = [
            'spec.name': 'ZlibCodecFactorySpec',
            'micronaut.server.netty.compression-threshold': 0,
        ]
        if (encoding in ['br', 'zstd', 'snappy']) {
            options['codec.extra'] = true
            options['micronaut.server.netty.compression-level'] = 4
            options['micronaut.server.netty.max-zstd-encode-size'] = 1048576
        }
        if (protocol != 'http1') {
            options += [
                'micronaut.http.client.alpn-modes': protocol,
                'micronaut.http.client.ssl.insecure-trust-all-certificates': true,
                'micronaut.server.ssl.enabled': true,
                'micronaut.server.ssl.port': 0,
                'micronaut.server.ssl.build-self-signed': true,
            ]
            if (protocol == 'h3') {
                options['micronaut.server.netty.listeners.http3.family'] = 'QUIC'
            } else {
                options['micronaut.server.http-version'] = '2.0'
            }
        }
        def server = ApplicationContext.run(EmbeddedServer, options)
        def client = server.applicationContext.createBean(HttpClient, server.URI)
        def factory = server.applicationContext.getBean(NettyZlibCodecFactory)
        String body = 'streaming codec construction '.repeat(128)
        def bytes = new ByteArrayOutputStream()
        (encoding == 'deflate' ? new java.util.zip.DeflaterOutputStream(bytes) : new GZIPOutputStream(bytes))
            .withCloseable { it.write(body.getBytes('UTF-8')) }

        expect:
        3.times {
            assert client.toBlocking().retrieve(HttpRequest.POST('/codec', bytes.toByteArray())
                .contentType(MediaType.TEXT_PLAIN_TYPE)
                .header('Content-Encoding', encoding)
                .header('Accept-Encoding', encoding), String) == body
        }
        factory.encoders.get() == 3
        factory.decoders.get() == 6
        factory.handlers.size() == 9
        if (factory instanceof ExtraFactory) {
            // Test providers use gzip internally to exercise routing without optional JNI libraries.
            assert getClass().classLoader.getResource('com/aayushatharva/brotli4j/Brotli4jLoader.class') == null
            assert getClass().classLoader.getResource('com/github/luben/zstd/Zstd.class') == null
            assert factory.calls == ["decode:$encoding", "encode:$encoding", "decode:$encoding"] * 3
        }

        cleanup:
        client?.close()
        server?.close()

        where:
        [protocol, encoding] << [['http1', 'h2', 'h3'], ['gzip', 'deflate', 'br', 'zstd', 'snappy']].combinations()
    }

    def 'disabled compression and identity bodies do not construct codecs'() {
        given:
        def server = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'ZlibCodecFactorySpec',
            'micronaut.server.netty.compression-threshold': -1,
        ])
        def client = server.applicationContext.createBean(HttpClient, server.URI)
        def factory = server.applicationContext.getBean(CountingFactory)

        expect:
        client.toBlocking().retrieve(HttpRequest.POST('/codec', 'identity')
            .contentType(MediaType.TEXT_PLAIN_TYPE).header('Accept-Encoding', 'gzip'), String) == 'identity'
        factory.encoders.get() == 0
        factory.decoders.get() == 0

        cleanup:
        client?.close()
        server?.close()
    }

    def 'direct client builder can select its own factory'() {
        given:
        def server = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'ZlibCodecFactorySpec',
            'micronaut.server.netty.compression-threshold': 0,
        ])
        def factory = new CountingFactory()
        def builder = io.micronaut.http.client.netty.DefaultHttpClient.builder()
        def client = builder.uri(server.URI).zlibCodecFactory(factory).build()
        String body = 'direct client '.repeat(128)

        expect:
        client.toBlocking().retrieve(HttpRequest.POST('/codec', body)
            .contentType(MediaType.TEXT_PLAIN_TYPE).header('Accept-Encoding', 'gzip'), String) == body
        factory.encoders.get() == 0
        factory.decoders.get() == 1

        cleanup:
        client?.close()
        server?.close()

    }

    @Singleton
    @Requires(property = 'spec.name', value = 'ZlibCodecFactorySpec')
    @Requires(missingProperty = 'codec.extra')
    static class CountingFactory implements NettyZlibCodecFactory {
        final AtomicInteger encoders = new AtomicInteger()
        final AtomicInteger decoders = new AtomicInteger()
        final Set<Object> handlers = ConcurrentHashMap.newKeySet()

        @Override
        ZlibEncoder newZlibEncoder(ZlibWrapper wrapper, int level, int windowBits, int memLevel) {
            encoders.incrementAndGet()
            def handler = ZlibCodecFactory.newZlibEncoder(wrapper, level, windowBits, memLevel)
            assert handlers.add(handler)
            handler
        }

        @Override
        ZlibDecoder newZlibDecoder(ZlibWrapper wrapper, int maxAllocation) {
            decoders.incrementAndGet()
            def handler = ZlibCodecFactory.newZlibDecoder(wrapper, maxAllocation)
            assert handlers.add(handler)
            handler
        }
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'ZlibCodecFactorySpec')
    @Requires(property = 'codec.extra', value = 'true')
    static class ExtraFactory extends CountingFactory {
        final List<String> calls = Collections.synchronizedList(new ArrayList<>())

        @Override
        boolean isBrotliAvailable() { true }

        @Override
        boolean isZstdAvailable() { true }

        private ChannelHandler encode(String encoding) {
            calls.add("encode:$encoding".toString())
            newZlibEncoder(ZlibWrapper.GZIP, 6, 15, 8)
        }

        private ChannelHandler decode(String encoding, int maxAllocation) {
            assert maxAllocation == 0
            calls.add("decode:$encoding".toString())
            newZlibDecoder(ZlibWrapper.GZIP, maxAllocation)
        }

        @Override
        ChannelHandler newBrotliEncoder() { encode('br') }

        @Override
        ChannelHandler newBrotliDecoder(int maxAllocation) { decode('br', maxAllocation) }

        @Override
        ChannelHandler newZstdEncoder(int compressionLevel, int maxEncodeSize) {
            assert compressionLevel == 4
            assert maxEncodeSize == 1048576
            encode('zstd')
        }

        @Override
        ChannelHandler newZstdDecoder(int maxAllocation) { decode('zstd', maxAllocation) }

        @Override
        ChannelHandler newSnappyFrameEncoder() { encode('snappy') }

        @Override
        ChannelHandler newSnappyFrameDecoder() { decode('snappy', 0) }
    }

    @Controller('/codec')
    @Requires(property = 'spec.name', value = 'ZlibCodecFactorySpec')
    static class EchoController {
        @Post(consumes = MediaType.TEXT_PLAIN, produces = MediaType.TEXT_PLAIN)
        String echo(@Body String body) { body }
    }
}
