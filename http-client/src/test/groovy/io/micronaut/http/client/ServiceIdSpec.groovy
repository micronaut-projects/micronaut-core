package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpAttributes
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpVersion
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.filter.ClientFilterChain
import io.micronaut.http.filter.HttpClientFilter
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import spock.lang.Specification

class ServiceIdSpec extends Specification {

    def 'service id set by declarative client'() {
        given:
        def serverCtx = ApplicationContext.run([
                'spec.name': 'ServiceIdSpec',
        ])
        def server = serverCtx.getBean(EmbeddedServer)
        server.start()
        def clientCtx = ApplicationContext.run([
                'spec.name': 'ServiceIdSpec',
                'micronaut.http.services.my-client-id.url': server.URI,
        ])
        def client = clientCtx.getBean(DeclarativeClient)
        def filter = clientCtx.getBean(ServiceIdFilter)

        expect:
        filter.serviceId == null
        client.index() == "foo"
        filter.serviceId == "my-client-id"

        cleanup:
        server.close()
        serverCtx.close()
        clientCtx.close()
    }

    def 'service id set by normal client'() {
        given:
        def serverCtx = ApplicationContext.run([
                'spec.name': 'ServiceIdSpec',
        ])
        def server = serverCtx.getBean(EmbeddedServer)
        server.start()
        def clientCtx = ApplicationContext.run([
                'spec.name': 'ServiceIdSpec',
                'micronaut.http.services.my-client-id.url': server.URI,
        ])
        def client = clientCtx.getBean(HttpClientRegistry).getClient(HttpVersion.HTTP_1_1, "my-client-id", null)
        def filter = clientCtx.getBean(ServiceIdFilter)

        expect:
        filter.serviceId == null
        client.toBlocking().exchange("/service-id", String).body() == "foo"
        filter.serviceId == "my-client-id"

        cleanup:
        server.close()
        serverCtx.close()
        clientCtx.close()
    }

    @Client(id = "my-client-id")
    static interface DeclarativeClient {
        @Get("/service-id")
        String index()
    }

    @Singleton
    @Requires(property = "spec.name", value = "ServiceIdSpec")
    @Controller("/service-id")
    static class ServiceIdController {
        @Get
        def index(HttpRequest<?> request) {
            return "foo"
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "ServiceIdSpec")
    @Filter(Filter.MATCH_ALL_PATTERN)
    static class ServiceIdFilter implements HttpClientFilter {
        String serviceId

        @Override
        Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request, ClientFilterChain chain) {
            serviceId = request.getAttribute(HttpAttributes.SERVICE_ID).orElse(null)
            return chain.proceed(request)
        }
    }
}
