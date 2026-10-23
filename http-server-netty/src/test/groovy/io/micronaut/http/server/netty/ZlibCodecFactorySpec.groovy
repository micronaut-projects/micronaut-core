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
    def 'replacement handles request and response streams for #protocol'(String protocol) {
        given:
        Map options = [
            'spec.name': 'ZlibCodecFactorySpec',
            'micronaut.server.netty.compression-threshold': 0,
        ]
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
        def factory = server.applicationContext.getBean(CountingFactory)
        String body = 'streaming codec construction '.repeat(128)
        def bytes = new ByteArrayOutputStream()
        new GZIPOutputStream(bytes).withCloseable { it.write(body.getBytes('UTF-8')) }

        expect:
        3.times {
            assert client.toBlocking().retrieve(HttpRequest.POST('/codec', bytes.toByteArray())
                .contentType(MediaType.TEXT_PLAIN_TYPE)
                .header('Content-Encoding', 'gzip')
                .header('Accept-Encoding', 'gzip'), String) == body
        }
        factory.encoders.get() == 3
        factory.decoders.get() == 6
        factory.handlers.size() == 9

        cleanup:
        client?.close()
        server?.close()

        where:
        protocol << ['http1', 'h2', 'h3']
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'ZlibCodecFactorySpec')
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

    @Controller('/codec')
    @Requires(property = 'spec.name', value = 'ZlibCodecFactorySpec')
    static class EchoController {
        @Post(consumes = MediaType.TEXT_PLAIN, produces = MediaType.TEXT_PLAIN)
        String echo(@Body String body) { body }
    }
}
