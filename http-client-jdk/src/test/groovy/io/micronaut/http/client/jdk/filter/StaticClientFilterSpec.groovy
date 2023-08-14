package io.micronaut.http.client.jdk.filter

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.io.socket.SocketUtils
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
import spock.lang.Specification

class StaticClientFilterSpec extends Specification {

    void "test only the correct filter applies"() {
        given:
        int randomPort = SocketUtils.findAvailableTcpPort()
        String url = "http://localhost:${randomPort}"
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer,
                [
                        'spec.name': StaticClientFilterSpec.simpleName,
                        'micronaut.server.port': "$randomPort",
                        'micronaut.http.services.a.url': url,
                        'micronaut.http.services.b.url': url,
                ]
        )
        ApplicationContext ctx = server.applicationContext
        ClientA a = ctx.getBean(ClientA)
        ClientB b = ctx.getBean(ClientB)

        expect:
        a.call() == "A"
        b.call() == "B"

        cleanup:
        server.close()
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
