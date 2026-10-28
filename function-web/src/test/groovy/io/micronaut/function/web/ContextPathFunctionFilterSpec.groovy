package io.micronaut.function.web

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.function.FunctionBean
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Filter
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

import java.net.http.HttpClient
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier

/**
 * Function routes are not mounted under the server context path; context path filters must still run on them.
 */
class ContextPathFunctionFilterSpec extends Specification {

    void "a context path filter guards function routes, which are not under the context path"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                    : 'ContextPathFunctionFilterSpec',
                'micronaut.server.context-path': '/api',
        ])
        HttpClient client = HttpClient.newHttpClient()
        def base = "http://localhost:${server.port}"
        AuthFilter.RUNS.set(0)

        when:
        def response = client.send(java.net.http.HttpRequest.newBuilder(URI.create("$base/cp-secret")).build(),
                java.net.http.HttpResponse.BodyHandlers.ofString())

        then: "the function must not be served without passing the filter"
        response.statusCode() == 401
        response.body() == 'denied-by-filter'
        AuthFilter.RUNS.get() == 1

        when:
        def authorized = client.send(java.net.http.HttpRequest.newBuilder(URI.create("$base/cp-secret")).header('X-Auth', 'ok').build(),
                java.net.http.HttpResponse.BodyHandlers.ofString())

        then: "the function URL is unchanged"
        authorized.statusCode() == 200
        authorized.body() == 'secret-value'

        cleanup:
        server?.close()
    }

    @Requires(property = 'spec.name', value = 'ContextPathFunctionFilterSpec')
    @FunctionBean("cp-secret")
    static class SecretFunction implements Supplier<String> {
        @Override
        String get() {
            return 'secret-value'
        }
    }

    @Requires(property = 'spec.name', value = 'ContextPathFunctionFilterSpec')
    @Filter('/**')
    static class AuthFilter implements HttpServerFilter {
        static final AtomicInteger RUNS = new AtomicInteger()

        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            RUNS.incrementAndGet()
            if (request.headers.get('X-Auth') == 'ok') {
                return chain.proceed(request)
            }
            return Mono.just(HttpResponse.unauthorized().body('denied-by-filter'))
        }
    }
}
