package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.async.publisher.Publishers
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Inject
import org.reactivestreams.Publisher
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class ProxyInterceptFullResponseSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer,
            ['spec.name': 'ProxyInterceptFullResponseSpec']
    )

    @Shared
    @AutoCleanup
    HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

    void "test a request expecting a full response intercepted by a proxy providing a streaming response"() {
        expect:
        client.toBlocking().retrieve("/proxy/request") == "from server"
    }

    @Requires(property = "spec.name", value = "ProxyInterceptFullResponseSpec")
    @Controller
    static class TestController {

        @Get("/intercepted")
        String get() {
            return "from server"
        }

        @Error(global = true, status = HttpStatus.NOT_FOUND)
        HttpResponse error(HttpRequest<?> request) {

        }
    }

    @Requires(property = "spec.name", value = "ProxyInterceptFullResponseSpec")
    @Filter("/proxy/**")
    static class TestFilter implements HttpServerFilter {

        @Inject @Client("/") ProxyHttpClient proxyHttpClient
        @Inject EmbeddedServer server

        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            MutableHttpRequest<?> req = request.mutate()
                    .uri(uri -> uri.scheme(server.getScheme())
                            .host(server.getHost())
                            .port(server.getPort())
                            .replacePath("/intercepted")
                    );
            return proxyHttpClient.proxy(req)
        }
    }
}
