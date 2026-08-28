package io.micronaut.http.server.netty.http2

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.core.io.buffer.ByteBuffer
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.StreamingHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import reactor.core.publisher.Flux
import spock.lang.Issue
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@MicronautTest
@Property(name = "micronaut.server.http-version", value = "2.0")
@Property(name = "micronaut.http.client.plaintext-mode", value = "h2c_prior_knowledge")
@Property(name = "micronaut.server.ssl.enabled", value = "false")
@Property(name = "spec.name", value = "H2cPriorKnowledgeClientSpec")
@Issue('https://github.com/micronaut-projects/micronaut-core/issues/10762')
class H2cPriorKnowledgeClientSpec extends Specification {
    @Inject
    EmbeddedServer embeddedServer

    @Inject
    HttpClient httpClient

    @Inject
    StreamingHttpClient streamingHttpClient

    void 'test using micronaut http client: retrieve over h2c prior knowledge'() {
        expect:
        httpClient.toBlocking().retrieve("http://localhost:${embeddedServer.port}/h2c-prior/test") == 'foo'
        httpClient.toBlocking().retrieve("http://localhost:${embeddedServer.port}/h2c-prior/testStream") == 'foo'
    }

    void 'test using micronaut http client: retrieve reverse over h2c prior knowledge'() {
        expect:
        httpClient.toBlocking().retrieve("http://localhost:${embeddedServer.port}/h2c-prior/testStream") == 'foo'
        httpClient.toBlocking().retrieve("http://localhost:${embeddedServer.port}/h2c-prior/test") == 'foo'
    }

    private String stream(String url) {
        def composed = new StringBuilder()
        def future = new CompletableFuture<Void>()
        streamingHttpClient.dataStream(HttpRequest.GET(url)).subscribe(new Subscriber<ByteBuffer<?>>() {
            @Override
            void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE)
            }

            @Override
            void onNext(ByteBuffer<?> byteBuffer) {
                composed.append(new String(byteBuffer.toByteArray(), StandardCharsets.UTF_8))
            }

            @Override
            void onError(Throwable t) {
                future.completeExceptionally(t)
            }

            @Override
            void onComplete() {
                future.complete(null)
            }
        })
        future.get(10, TimeUnit.SECONDS)
        return composed.toString()
    }

    void 'test using micronaut http client: stream over h2c prior knowledge'() {
        expect:
        stream("http://localhost:${embeddedServer.port}/h2c-prior/test") == 'foo'
        stream("http://localhost:${embeddedServer.port}/h2c-prior/testStream") == 'foo'
    }

    void 'test using micronaut http client: stream reverse over h2c prior knowledge'() {
        expect:
        stream("http://localhost:${embeddedServer.port}/h2c-prior/testStream") == 'foo'
        stream("http://localhost:${embeddedServer.port}/h2c-prior/test") == 'foo'
    }

    @Controller('/h2c-prior')
    @Requires(property = 'spec.name', value = 'H2cPriorKnowledgeClientSpec')
    static class TestController {
        @Get('/test')
        String test(HttpRequest<?> request) {
            if (request.httpVersion != io.micronaut.http.HttpVersion.HTTP_2_0) {
                throw new IllegalArgumentException('Request should be HTTP 2.0')
            }
            return 'foo'
        }

        @Get('/testStream')
        Publisher<byte[]> testStream(HttpRequest<?> request) {
            if (request.httpVersion != io.micronaut.http.HttpVersion.HTTP_2_0) {
                throw new IllegalArgumentException('Request should be HTTP 2.0')
            }
            return Flux.create { sink ->
                new Thread({
                    sink.next('f'.getBytes(StandardCharsets.UTF_8))
                    TimeUnit.SECONDS.sleep(1)
                    sink.next('oo'.getBytes(StandardCharsets.UTF_8))
                    sink.complete()
                }).start()
            }
        }
    }
}
