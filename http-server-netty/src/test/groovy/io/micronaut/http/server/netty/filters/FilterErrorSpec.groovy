package io.micronaut.http.server.netty.filters

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.async.publisher.Publishers
import io.micronaut.core.util.StringUtils
import io.micronaut.http.HttpAttributes
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Status
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.web.router.MethodBasedRouteMatch
import io.micronaut.web.router.RouteMatch
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class FilterErrorSpec extends Specification {

    void "test errors emitted from filters interacting with exception handlers"() {
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': FilterErrorSpec.simpleName])
        def ctx = server.applicationContext
        HttpClient client = ctx.createBean(HttpClient, server.getURL())
        First first = ctx.getBean(First)
        Next next = ctx.getBean(Next)

        when:
        HttpResponse<String> response = Flux.from(client.exchange("/filter-error-spec", String))
                .onErrorResume(t -> {
                    if (t instanceof HttpClientResponseException) {
                        return Flux.just(((HttpClientResponseException) t).response)
                    }
                    throw t
                })
                .blockFirst()

        then:
        response.status() == HttpStatus.BAD_REQUEST
        response.body() == "from filter exception handler"
        first.executedCount.get() == 1
        first.response.getAndSet(null) == null
        next.executedCount.get() == 0

        when:
        response = Flux.from(client.exchange(HttpRequest.GET("/filter-error-spec").header("X-Passthru", "true"), String))
                .onErrorResume(t -> {
                    if (t instanceof HttpClientResponseException) {
                        return Flux.just(((HttpClientResponseException)t).response)
                    }
                    throw t
                })
                .blockFirst()
        def firstResponse = first.response.getAndSet(null)

        then:
        response.status() == HttpStatus.BAD_REQUEST
        response.body() == "from NEXT filter exception handler"
        first.executedCount.get() == 2
        next.executedCount.get() == 1
        firstResponse.status() == HttpStatus.BAD_REQUEST

        cleanup:
        client.close()
        ctx.close()
    }

    void "test non once per request filter throwing error does not loop"() {
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': FilterErrorSpec.simpleName + '2'])
        def ctx = server.applicationContext
        HttpClient client = ctx.createBean(HttpClient, server.getURL())
        FirstEvery filter = ctx.getBean(FirstEvery)

        when:
        HttpResponse<String> response = Flux.from(client.exchange("/filter-error-spec", String))
                .onErrorResume(t -> {
                    if (t instanceof HttpClientResponseException) {
                        return Flux.just(((HttpClientResponseException) t).response)
                    }
                    throw t
                })
                .blockFirst()

        then:
        response.status() == HttpStatus.BAD_REQUEST
        response.body() == "from filter exception handler"
        filter.executedCount.get() == 1

        cleanup:
        client.close()
        ctx.close()
    }

    void "test filter throwing exception handled by exception handler throwing exception"() {
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': FilterErrorSpec.simpleName + '3'])
        def ctx = server.applicationContext
        HttpClient client = ctx.createBean(HttpClient, server.getURL())
        ExceptionException filter = ctx.getBean(ExceptionException)

        when:
        HttpResponse<String> response = Flux.from(client.exchange("/filter-error-spec-3", String))
                .onErrorResume(t -> {
                    if (t instanceof HttpClientResponseException) {
                        return Flux.just(((HttpClientResponseException) t).response)
                    }
                    throw t
                })
                .blockFirst()
        def filterResponse = filter.response.getAndSet(null)

        then:
        response.status() == HttpStatus.INTERNAL_SERVER_ERROR
        response.body().contains("from exception handler")
        filter.executedCount.get() == 1
        filterResponse.status() == HttpStatus.INTERNAL_SERVER_ERROR

        cleanup:
        client.close()
        ctx.close()
    }

    void "test the error route is the route match"() {
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': FilterErrorSpec.simpleName + '4'])
        def ctx = server.applicationContext
        HttpClient client = ctx.createBean(HttpClient, server.getURL())
        ExceptionRoute filter = ctx.getBean(ExceptionRoute)

        when:
        HttpResponse<String> response = Flux.from(client.exchange("/filter-error-spec-4/exception", String))
                .blockFirst()
        def match = filter.routeMatch.getAndSet(null)

        then:
        response.status() == HttpStatus.OK
        match instanceof MethodBasedRouteMatch
        ((MethodBasedRouteMatch) match).getName() == "testException"

        when:
        response = Flux.from(client.exchange("/filter-error-spec-4/status", String))
                .blockFirst()
        match = filter.routeMatch.getAndSet(null)

        then:
        response.status() == HttpStatus.OK
        match instanceof MethodBasedRouteMatch
        ((MethodBasedRouteMatch) match).getName() == "testStatus"

        cleanup:
        client.close()
        ctx.close()
    }

    @Requires(property = 'spec.name', value = 'FilterErrorSpec')
    @Filter("/**")
    static class First implements HttpServerFilter {

        AtomicInteger executedCount = new AtomicInteger(0)
        AtomicReference<MutableHttpResponse<?>> response = new AtomicReference<>()

        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            executedCount.incrementAndGet()
            if (StringUtils.isTrue(request.getHeaders().get("X-Passthru"))) {
                return Publishers.then(chain.proceed(request), response::set)
            }
            return Publishers.just(new FilterException())
        }

        @Override
        int getOrder() {
            10
        }
    }

    @Requires(property = 'spec.name', value = 'FilterErrorSpec')
    @Filter("/**")
    static class Next implements HttpServerFilter {

        AtomicInteger executedCount = new AtomicInteger(0)

        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            executedCount.incrementAndGet()
            return Publishers.just(new NextFilterException())
        }

        @Override
        int getOrder() {
            20
        }
    }

    @Requires(property = 'spec.name', value = 'FilterErrorSpec2')
    @Filter("/**")
    static class FirstEvery implements HttpServerFilter {

        AtomicInteger executedCount = new AtomicInteger(0)

        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            executedCount.incrementAndGet()
            return Publishers.just(new FilterException())
        }

        @Override
        int getOrder() {
            10
        }
    }

    @Requires(property = 'spec.name', value = 'FilterErrorSpec3')
    @Filter("/**")
    static class ExceptionException implements HttpServerFilter {

        AtomicInteger executedCount = new AtomicInteger(0)
        AtomicReference<MutableHttpResponse<?>> response = new AtomicReference<>()

        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            executedCount.incrementAndGet()
            return Publishers.then(chain.proceed(request), response::set)
        }

        @Override
        int getOrder() {
            10
        }
    }

    @Requires(property = 'spec.name', value = 'FilterErrorSpec4')
    @Filter("/**")
    static class ExceptionRoute implements HttpServerFilter {


        AtomicReference<RouteMatch<?>> routeMatch = new AtomicReference<>()

        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            return Publishers.then(chain.proceed(request), { resp ->
                routeMatch.set(resp.getAttribute(HttpAttributes.ROUTE_MATCH, RouteMatch).get())
            })
        }

        @Override
        int getOrder() {
            10
        }
    }

    @Controller("/filter-error-spec")
    static class NeverReachedController {

        @Get
        String get() {
            return "OK"
        }

    }

    @Controller("/filter-error-spec-3")
    static class HandledByHandlerController {

        @Get
        String get() {
            throw new FilterExceptionException()
        }

    }

    @Controller("/filter-error-spec-4")
    static class HandledByErrorRouteController {

        @Get("/exception")
        String getException() {
            throw new FilterExceptionException()
        }

        @Get("/status")
        HttpStatus getStatus() {
            return HttpStatus.NOT_FOUND
        }

        @Error(exception = FilterExceptionException)
        @Status(HttpStatus.OK)
        void testException() {}

        @Error(status = HttpStatus.NOT_FOUND)
        @Status(HttpStatus.OK)
        void testStatus() {}
    }
}
