package io.micronaut.http.filter

import io.micronaut.core.convert.ConversionService
import io.micronaut.core.execution.ExecutionFlow
import io.micronaut.core.type.Argument
import io.micronaut.core.type.ReturnType
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpRequestWrapper
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.MutableHttpRequestWrapper
import io.micronaut.http.bind.DefaultRequestBinderRegistry
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * A filter method given the mutable view of a request that is not mutable continues with the view
 * when it changed its URI in place: without parsing the URI of the request when the view knows its
 * URI was not set, and when the result of an asynchronous filter completes.
 */
class MethodFilterChangedUriSpec extends Specification {

    def 'a filter method that does not change the URI of the view does not parse the URI of the request'() {
        given:
        def uriCalls = new AtomicInteger()
        def request = new ImmutableRequest(HttpRequest.GET("/original"), uriCalls)
        def seen = []
        def runner = new FilterRunner([
                before(ReturnType.of(void), [Argument.of(MutableHttpRequest)]) { MutableHttpRequest<?> view ->
                    view.getHeaders().add("X-Filtered", "true")
                    null
                }
        ], (req, ctx) -> {
            seen << req
            ExecutionFlow.just(HttpResponse.ok())
        })

        when:
        def response = runner.run(request).tryCompleteValue()

        then:
        response.status() == HttpStatus.OK
        seen == [request]
        uriCalls.get() == 0
    }

    def 'a filter method that changes the URI of the view in place continues with the view'() {
        given:
        def request = new ImmutableRequest(HttpRequest.GET("/original"), new AtomicInteger())
        def seen = []
        def runner = new FilterRunner([
                before(ReturnType.of(void), [Argument.of(MutableHttpRequest)]) { MutableHttpRequest<?> view ->
                    view.uri(URI.create("/changed"))
                    null
                }
        ], (req, ctx) -> {
            seen << req.getUri().toString()
            ExecutionFlow.just(HttpResponse.ok())
        })

        when:
        def response = runner.run(request).tryCompleteValue()

        then:
        response.status() == HttpStatus.OK
        seen == ["/changed"]
    }

    def 'an asynchronous filter method that changes the URI of the view later continues with the view'() {
        given:
        def request = new ImmutableRequest(HttpRequest.GET("/original"), new AtomicInteger())
        def trigger = new CompletableFuture<Object>()
        def seen = []
        def runner = new FilterRunner([
                before(ReturnType.of(Publisher, Argument.of(HttpResponse)), [Argument.of(MutableHttpRequest)]) { MutableHttpRequest<?> view ->
                    Mono.fromFuture(trigger).then(Mono.fromRunnable { view.uri(URI.create("/changed-later")) })
                }
        ], (req, ctx) -> {
            seen << req.getUri().toString()
            ExecutionFlow.just(HttpResponse.ok())
        })

        when:
        def result = runner.run(request).toCompletableFuture()
        trigger.complete("go")

        then:
        result.get(10, TimeUnit.SECONDS).status() == HttpStatus.OK
        seen == ["/changed-later"]
    }

    private static def before(ReturnType returnType, List<Argument> arguments, Closure<?> closure) {
        return MethodFilter.prepareFilterMethod(ConversionService.SHARED, null, new LambdaExecutable(closure, arguments.toArray(new Argument[0]), returnType), false, new FilterOrder.Fixed(0), new DefaultRequestBinderRegistry(ConversionService.SHARED), null)
    }

    /**
     * A request that is not mutable, whose mutable view knows whether its URI was set, and that
     * counts how often its URI is parsed.
     */
    static class ImmutableRequest extends HttpRequestWrapper<Object> {
        private final AtomicInteger uriCalls

        ImmutableRequest(HttpRequest<Object> delegate, AtomicInteger uriCalls) {
            super(delegate)
            this.uriCalls = uriCalls
        }

        @Override
        URI getUri() {
            uriCalls.incrementAndGet()
            return super.getUri()
        }

        @Override
        MutableHttpRequest<Object> mutate() {
            return new View(this)
        }
    }

    static class View extends MutableHttpRequestWrapper<Object> implements UriChangeAwareRequest {
        private boolean uriSet

        View(HttpRequest<Object> delegate) {
            super(ConversionService.SHARED, delegate)
        }

        @Override
        MutableHttpRequest<Object> uri(URI uri) {
            uriSet = true
            return super.uri(uri)
        }

        @Override
        boolean isUriSet() {
            return uriSet
        }
    }
}
