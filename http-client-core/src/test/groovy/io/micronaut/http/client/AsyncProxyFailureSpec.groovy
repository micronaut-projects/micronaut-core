package io.micronaut.http.client

import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpResponse
import org.reactivestreams.Publisher
import spock.lang.Specification

import java.util.concurrent.CompletionException

class AsyncProxyFailureSpec extends Specification {
    void 'a synchronously failing reactive proxy completes the async stage exceptionally'() {
        given:
        def failure = new UnsupportedOperationException('unsupported options')
        ProxyHttpClient reactive = new ProxyHttpClient() {
            @Override
            Publisher<MutableHttpResponse<?>> proxy(HttpRequest<?> request) {
                throw failure
            }
        }

        when:
        def stage = reactive.toAsyncProxy().proxy(HttpRequest.GET('/'))

        then:
        stage.toCompletableFuture().isCompletedExceptionally()

        when:
        stage.toCompletableFuture().join()

        then:
        def exception = thrown(CompletionException)
        exception.cause.is(failure)
    }
}
