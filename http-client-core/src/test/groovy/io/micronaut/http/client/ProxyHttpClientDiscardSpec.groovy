package io.micronaut.http.client

import io.micronaut.http.ByteBodyHttpResponse
import io.micronaut.http.ByteBodyHttpResponseWrapper
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpResponseWrapper
import io.micronaut.http.MutableByteBodyHttpResponse
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.body.CloseableByteBody
import org.reactivestreams.Publisher

import reactor.core.publisher.Mono
import spock.lang.Specification

/**
 * The runtime-neutral discard of a proxied response that is not relayed: the default
 * implementation closes the response that carries the bytes, or a closeable body, and never
 * throws.
 */
class ProxyHttpClientDiscardSpec extends Specification {

    static class ReactiveClient implements ProxyHttpClient {
        @Override
        Publisher<MutableHttpResponse<?>> proxy(HttpRequest<?> request) {
            return Mono.just(HttpResponse.ok('done'))
        }
    }

    static class DiscardingClient extends ReactiveClient {
        List<HttpResponse<?>> discarded = []

        @Override
        void discard(HttpResponse<?> response) {
            discarded << response
        }
    }

    void "a response carrying bytes is closed"() {
        given:
        CloseableByteBody body = Mock()
        ByteBodyHttpResponse<?> response = ByteBodyHttpResponseWrapper.wrap(HttpResponse.ok(), body)

        when:
        new ReactiveClient().discard(response)

        then:
        1 * body.close()
    }

    void "the mutable response of a proxied exchange is closed"() {
        given:
        CloseableByteBody body = Mock()
        MutableHttpResponse<?> response = MutableByteBodyHttpResponse.of(HttpResponse.ok(), body)

        when:
        new ReactiveClient().discard(response)

        then:
        1 * body.close()
    }

    void "a wrapped response is unwrapped and closed"() {
        given:
        CloseableByteBody body = Mock()
        HttpResponse<?> response = new HttpResponseWrapper<>(new HttpResponseWrapper<>(MutableByteBodyHttpResponse.of(HttpResponse.ok(), body)))

        when:
        new ReactiveClient().discard(response)

        then:
        1 * body.close()
    }

    void "a closeable body is closed (#description)"() {
        given:
        AutoCloseable body = description == 'closeable' ? Mock(Closeable) : Mock(AutoCloseable)

        when:
        new ReactiveClient().discard(HttpResponse.ok(body))

        then:
        1 * body.close()

        where:
        description << ['closeable', 'auto closeable']
    }

    void "a response without resources is left alone"() {
        when:
        new ReactiveClient().discard(HttpResponse.ok('text'))
        new ReactiveClient().discard(HttpResponse.noContent())

        then:
        noExceptionThrown()
    }

    void "a failure to close is swallowed (#description)"() {
        given:
        CloseableByteBody body = Mock()
        body.close() >> { throw new IllegalStateException('already closed') }
        AutoCloseable closeable = Mock()
        closeable.close() >> { throw new IOException('closed') }
        HttpResponse<?> response = description == 'bytes'
            ? ByteBodyHttpResponseWrapper.wrap(HttpResponse.ok(), body)
            : HttpResponse.ok(closeable)

        when:
        new ReactiveClient().discard(response)

        then:
        noExceptionThrown()

        where:
        description << ['bytes', 'body']
    }

    void "the async view of a reactive client discards through the client"() {
        given:
        DiscardingClient client = new DiscardingClient()
        HttpResponse<?> response = HttpResponse.ok('text')

        when:
        client.toAsyncProxy().discard(response)

        then:
        client.discarded == [response]
    }

    void "the default async discard closes the response"() {
        given:
        CloseableByteBody body = Mock()
        AsyncProxyHttpClient client = new ReactiveClient().toAsyncProxy()

        when:
        client.discard(MutableByteBodyHttpResponse.of(HttpResponse.ok(), body))

        then:
        1 * body.close()
    }
}
