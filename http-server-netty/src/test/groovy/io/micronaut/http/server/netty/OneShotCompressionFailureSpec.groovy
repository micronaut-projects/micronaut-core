package io.micronaut.http.server.netty

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxy
import ch.qos.logback.core.AppenderBase
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.ServerHttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.netty.compression.NettyZlibCodecFactory
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.handler.codec.compression.ZlibWrapper
import io.netty.util.IllegalReferenceCountException
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * A codec provider whose one-shot compression throws must fail the response without releasing its body twice,
 * and without breaking the fallback response.
 */
class OneShotCompressionFailureSpec extends Specification {
    static final String BODY = 'one-shot compression failure '.repeat(64)

    def 'a failing provider on an HTTP/1 full response releases the body once'() {
        given:
        def errors = new ErrorAppender()
        def root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
        errors.start()
        root.addAppender(errors)
        def server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'OneShotCompressionFailureSpec',
                                                             'micronaut.server.netty.compression-threshold': 0])
        OneShotController.completion = new CompletableFuture<>()
        def client = server.applicationContext.createBean(HttpClient, server.URI)

        when:
        client.toBlocking().retrieve(HttpRequest.GET('/one-shot/full').header('Accept-Encoding', 'gzip'), String)

        then:
        thrown(Exception)
        // the completion callback sees the failure rather than a successful response
        OneShotController.completion.get(10, TimeUnit.SECONDS) instanceof IllegalStateException
        // the connection is closed rather than the next request seeing a half-released response
        client.toBlocking().retrieve(HttpRequest.GET('/one-shot/plain'), String) == 'plain'
        !errors.referenceCountFailures()

        cleanup:
        root.detachAppender(errors)
        client?.close()
        server?.close()
    }

    def 'a failing provider on an HTTP/2 full response still sends the fallback error response'() {
        given:
        def server = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'OneShotCompressionFailureSpec',
            'micronaut.server.netty.compression-threshold': 0,
            'micronaut.http.client.alpn-modes': 'h2',
            'micronaut.http.client.ssl.insecure-trust-all-certificates': true,
            'micronaut.server.ssl.enabled': true,
            'micronaut.server.ssl.port': 0,
            'micronaut.server.ssl.build-self-signed': true,
            'micronaut.server.http-version': '2.0',
        ])
        def client = server.applicationContext.createBean(HttpClient, server.URI)

        when:
        client.toBlocking().retrieve(HttpRequest.GET('/one-shot/full').header('Accept-Encoding', 'gzip'), String)

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.INTERNAL_SERVER_ERROR

        cleanup:
        client?.close()
        server?.close()
    }

    def 'a failing encoder on an HTTP/2 streamed response resets the stream'() {
        given:
        def errors = new ErrorAppender()
        def root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
        errors.start()
        root.addAppender(errors)
        def server = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'OneShotCompressionFailureSpec-encoder',
            'micronaut.server.netty.compression-threshold': 0,
            'micronaut.http.client.alpn-modes': 'h2',
            'micronaut.http.client.ssl.insecure-trust-all-certificates': true,
            'micronaut.server.ssl.enabled': true,
            'micronaut.server.ssl.port': 0,
            'micronaut.server.ssl.build-self-signed': true,
            'micronaut.server.http-version': '2.0',
        ])
        OneShotController.completion = new CompletableFuture<>()
        def client = server.applicationContext.createBean(HttpClient, server.URI)

        when:
        client.toBlocking().retrieve(HttpRequest.GET('/one-shot/stream').header('Accept-Encoding', 'gzip'), String)

        then:
        thrown(Exception)
        // the encoder is created with the first piece, after the headers went out
        OneShotController.completion.get(10, TimeUnit.SECONDS) instanceof IllegalStateException
        // the failure stays with the stream rather than escaping into the routing of an error response
        !errors.events.any { it.formattedMessage.contains('Failed to build error response') }

        cleanup:
        root.detachAppender(errors)
        client?.close()
        server?.close()
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'OneShotCompressionFailureSpec-encoder')
    static class ThrowingEncoderFactory implements NettyZlibCodecFactory {
        @Override
        io.netty.handler.codec.compression.ZlibEncoder newZlibEncoder(ZlibWrapper wrapper, int level, int windowBits, int memLevel) {
            throw new IllegalStateException('encoder failure')
        }

        @Override
        io.netty.handler.codec.compression.ZlibDecoder newZlibDecoder(ZlibWrapper wrapper, int maxAllocation) {
            io.netty.handler.codec.compression.ZlibCodecFactory.newZlibDecoder(wrapper, maxAllocation)
        }
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'OneShotCompressionFailureSpec')
    static class ThrowingFactory implements NettyZlibCodecFactory {
        @Override
        io.netty.handler.codec.compression.ZlibEncoder newZlibEncoder(ZlibWrapper wrapper, int level, int windowBits, int memLevel) {
            io.netty.handler.codec.compression.ZlibCodecFactory.newZlibEncoder(wrapper, level, windowBits, memLevel)
        }

        @Override
        io.netty.handler.codec.compression.ZlibDecoder newZlibDecoder(ZlibWrapper wrapper, int maxAllocation) {
            io.netty.handler.codec.compression.ZlibCodecFactory.newZlibDecoder(wrapper, maxAllocation)
        }

        @Override
        ByteBuf compressZlib(ZlibWrapper wrapper, int level, int windowBits, int memLevel, ByteBuf body, ByteBufAllocator allocator) {
            throw new IllegalStateException('provider failure')
        }
    }

    @Controller('/one-shot')
    @Requires(property = 'spec.name', pattern = 'OneShotCompressionFailureSpec.*')
    static class OneShotController {
        static volatile CompletableFuture<Throwable> completion = new CompletableFuture<>()

        @Get(uri = '/full', produces = MediaType.TEXT_PLAIN)
        String full(HttpRequest<?> request) {
            def future = completion
            ((ServerHttpRequest<?>) request).whenResponseCompleted { Throwable t -> future.complete(t) }
            BODY
        }

        @Get(uri = '/stream', produces = MediaType.TEXT_PLAIN)
        Publisher<String> stream(HttpRequest<?> request) {
            def future = completion
            ((ServerHttpRequest<?>) request).whenResponseCompleted { Throwable t -> future.complete(t) }
            Flux.just(BODY, BODY)
        }

        @Get(uri = '/plain', produces = MediaType.TEXT_PLAIN)
        String plain() {
            'plain'
        }
    }

    static class ErrorAppender extends AppenderBase<ILoggingEvent> {
        final List<ILoggingEvent> events = Collections.synchronizedList([])

        @Override
        protected void append(ILoggingEvent event) {
            events.add(event)
        }

        boolean referenceCountFailures() {
            events.any { event ->
                def proxy = event.throwableProxy instanceof ThrowableProxy ? ((ThrowableProxy) event.throwableProxy).throwable : null
                for (Throwable t = proxy; t != null; t = t.cause) {
                    if (t instanceof IllegalReferenceCountException) {
                        return true
                    }
                }
                event.formattedMessage?.contains('IllegalReferenceCountException')
            }
        }
    }
}
