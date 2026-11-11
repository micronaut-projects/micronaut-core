package io.micronaut.function.web

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.function.FunctionBean
import io.micronaut.core.annotation.Nullable
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier

/**
 * Function routes are not mounted under the server context path; context path filters must still run on them.
 */
class ContextPathFunctionFilterSpec extends Specification {

    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
            'spec.name'                    : 'ContextPathFunctionFilterSpec',
            'micronaut.server.context-path': '/api',
    ])

    @AutoCleanup
    HttpClient client = HttpClient.create(new URL("http://localhost:${server.port}"))

    void "a context path filter guards function routes, which are not under the context path"() {
        given:
        AuthFilter.RUNS.set(0)

        when:
        client.toBlocking().exchange(HttpRequest.GET('/cp-secret'), Argument.of(String), Argument.of(String))

        then: "the function must not be served without passing the filter"
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.UNAUTHORIZED
        e.response.getBody(String).get() == 'denied-by-filter'
        AuthFilter.RUNS.get() == 1

        when:
        HttpResponse<String> authorized = client.toBlocking().exchange(HttpRequest.GET('/cp-secret').header('X-Auth', 'ok'), String)

        then: "the function URL is unchanged"
        authorized.status() == HttpStatus.OK
        authorized.body() == 'secret-value'
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
