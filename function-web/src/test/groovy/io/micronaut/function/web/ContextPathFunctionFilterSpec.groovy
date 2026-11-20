package io.micronaut.function.web

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.function.FunctionBean
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter
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
        client?.close()
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
    @ServerFilter('/**')
    static class AuthFilter {
        static final AtomicInteger RUNS = new AtomicInteger()

        @RequestFilter
        @Nullable
        MutableHttpResponse<?> filter(HttpRequest<?> request) {
            RUNS.incrementAndGet()
            if (request.headers.get('X-Auth') == 'ok') {
                return null
            }
            return HttpResponse.unauthorized().body('denied-by-filter')
        }
    }
}
