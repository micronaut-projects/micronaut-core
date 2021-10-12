package io.micronaut.http.client.filter

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.filter.ClientFilterChain
import io.micronaut.http.filter.HttpClientFilter
import io.micronaut.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
import spock.lang.Retry
import spock.lang.Specification

class StaticClientFilterSpec extends Specification {

    @Retry // can fail on CI due to port binding race condition, so retry
    void "test only the correct filter applies"() {
        def server = ApplicationContext.run(EmbeddedServer,
        ['spec.name': StaticClientFilterSpec.simpleName,
         'test-port': '${random.port}',
         'micronaut.server.port': '${test-port}',
         'micronaut.http.services.a.url': 'http://localhost:${test-port}',
         'micronaut.http.services.b.url': 'http://localhost:${test-port}',
        ])
        def ctx = server.applicationContext
        ClientA a = ctx.getBean(ClientA)
        ClientB b = ctx.getBean(ClientB)

        expect:
        a.call() == "A"
        b.call() == "B"

        cleanup:
        ctx.close()
    }

    @Requires(property = "spec.name", value = "StaticClientFilterSpec")
    @Client(id = "a")
    static interface ClientA {

        @Get("/temp")
        String call()
    }

    @Requires(property = "spec.name", value = "StaticClientFilterSpec")
    @Client(id = "b")
    static interface ClientB {

        @Get("/temp")
        String call()
    }

    @Requires(property = "spec.name", value = "StaticClientFilterSpec")
    @Filter(serviceId = "a")
    static class AFilter implements HttpClientFilter {
        @Override
        Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request, ClientFilterChain chain) {
            return chain.proceed(request.header("Filtered-Through", "A"))
        }
    }

    @Requires(property = "spec.name", value = "StaticClientFilterSpec")
    @Filter(serviceId = "b")
    static class BFilter implements HttpClientFilter {
        @Override
        Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request, ClientFilterChain chain) {
            return chain.proceed(request.header("Filtered-Through", "B"))
        }
    }

    @Requires(property = "spec.name", value = "StaticClientFilterSpec")
    @Controller
    static class Echo {

        @Get("/temp")
        String echo(@Header("Filtered-Through") String filter) {
            return filter
        }
    }
}
