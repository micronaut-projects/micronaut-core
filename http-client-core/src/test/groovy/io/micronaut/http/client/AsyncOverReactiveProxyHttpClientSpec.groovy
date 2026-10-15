package io.micronaut.http.client

import io.micronaut.core.execution.DelayedExecutionFlow
import io.micronaut.core.execution.ExecutionFlow
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.core.propagation.PropagatedContextElement
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.client.internal.RawResponseFuture
import org.jspecify.annotations.Nullable
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class AsyncOverReactiveProxyHttpClientSpec extends Specification {

    static class ReactiveClient implements ProxyHttpClient {
        HttpRequest<?> proxied

        @Override
        Publisher<MutableHttpResponse<?>> proxy(HttpRequest<?> request) {
            proxied = request
            return Mono.just(HttpResponse.ok('done'))
        }
    }

    static class CloseableClient extends ReactiveClient implements Closeable {
        int closed

        @Override
        void close() {
            closed++
        }
    }

    static class AutoCloseableClient extends ReactiveClient implements AutoCloseable {
        Exception failure

        @Override
        void close() throws Exception {
            if (failure != null) {
                throw failure
            }
        }
    }

    static class MyElement implements PropagatedContextElement {
    }

    void "the async view completes with the response of the publisher"() {
        given:
        def reactive = new ReactiveClient()
        def request = HttpRequest.GET('/x')

        when:
        MutableHttpResponse<?> response = reactive.toAsyncProxy().proxy(request).toCompletableFuture().get(5, TimeUnit.SECONDS)

        then:
        response.body() == 'done'
        reactive.proxied.is(request)
    }

    void "the async view closes a closeable client"() {
        given:
        def reactive = new CloseableClient()

        when:
        reactive.toAsyncProxy().close()

        then:
        reactive.closed == 1
    }

    void "the async view closes an auto closeable client"() {
        given:
        def reactive = new AutoCloseableClient(failure: failure)

        when:
        reactive.toAsyncProxy().close()

        then:
        def e = thrown(expected)
        e.is(failure) || e.cause.is(failure)

        where:
        failure                          | expected
        new IOException('io')            | IOException
        new IllegalStateException('ise') | IllegalStateException
        new Exception('checked')         | IOException
    }

    void "an auto closeable client that closes is fine and a plain client is not closed"() {
        when:
        new AutoCloseableClient().toAsyncProxy().close()
        new ReactiveClient().toAsyncProxy().close()

        then:
        noExceptionThrown()
    }

    void "the async view needs a client"() {
        when:
        new AsyncOverReactiveProxyHttpClient(null)

        then:
        thrown(NullPointerException)
    }

    void "the factory creates the async view of its proxy client"() {
        given:
        def reactive = new ReactiveClient()
        List<URL> urls = []
        List<HttpClientConfiguration> configurations = []
        ProxyHttpClientFactory factory = new ProxyHttpClientFactory() {
            @Override
            ProxyHttpClient createProxyClient(@Nullable URL url) {
                urls << url
                return reactive
            }

            @Override
            ProxyHttpClient createProxyClient(@Nullable URL url, HttpClientConfiguration configuration) {
                urls << url
                configurations << configuration
                return reactive
            }
        }
        def configuration = new DefaultHttpClientConfiguration()

        when:
        def a = factory.createAsyncProxyClient(URI.create('http://localhost:8080'))
        def b = factory.createAsyncProxyClient(null, configuration)

        then:
        a instanceof AsyncOverReactiveProxyHttpClient
        b instanceof AsyncOverReactiveProxyHttpClient
        urls == [new URL('http://localhost:8080'), null]
        configurations == [configuration]

        when:
        factory.createAsyncProxyClient(URI.create('relative'))

        then:
        thrown(IllegalArgumentException)
    }

    void "the future of a proxied flow releases once when it completes"() {
        given:
        DelayedExecutionFlow<MutableHttpResponse<?>> flow = DelayedExecutionFlow.create()
        def released = new AtomicInteger()
        def future = RawResponseFuture.ofMutable(flow, released::incrementAndGet, PropagatedContext.empty()).toCompletableFuture()

        when:
        flow.complete(HttpResponse.ok('x'))

        then:
        future.get(5, TimeUnit.SECONDS).body() == 'x'
        released.get() == 1

        when:
        future.cancel(true)

        then:
        released.get() == 1
    }

    void "cancelling the future of a proxied flow releases and cancels the flow"() {
        given:
        DelayedExecutionFlow<MutableHttpResponse<?>> flow = DelayedExecutionFlow.create()
        def released = new AtomicInteger()
        def future = RawResponseFuture.ofMutable(flow, released::incrementAndGet, PropagatedContext.empty()).toCompletableFuture()

        when:
        future.cancel(true)

        then:
        future.isCancelled()
        released.get() == 1
    }

    void "the future of a proxied flow completes within the context of the caller"() {
        given:
        def element = new MyElement()
        PropagatedContext context = PropagatedContext.empty().plus(element)
        DelayedExecutionFlow<MutableHttpResponse<?>> flow = DelayedExecutionFlow.create()
        def future = RawResponseFuture.ofMutable(flow, null, context).toCompletableFuture()
        List<Boolean> seen = []
        def dependent = future.thenApply { r ->
            seen << PropagatedContext.find().map { it.find(MyElement).orElse(null) == element }.orElse(false)
            return r
        }

        when:
        flow.complete(HttpResponse.ok())

        then:
        dependent.get(5, TimeUnit.SECONDS) != null
        seen == [true]
    }

    void "the future of a failed proxied flow fails"() {
        given:
        def failure = new IllegalStateException('x')
        def future = RawResponseFuture.ofMutable(ExecutionFlow.<MutableHttpResponse<?>> error(failure), null, PropagatedContext.empty()).toCompletableFuture()

        when:
        future.join()

        then:
        def e = thrown(Exception)
        e.cause.is(failure)
    }
}
