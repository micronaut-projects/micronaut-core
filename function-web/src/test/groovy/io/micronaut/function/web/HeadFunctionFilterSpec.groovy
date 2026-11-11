package io.micronaut.function.web

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.function.FunctionBean
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
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
 * The HEAD route of a function runs the function like GET does, so GET-scoped filters apply to it.
 */
class HeadFunctionFilterSpec extends Specification {

    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'HeadFunctionFilterSpec'])

    @AutoCleanup
    HttpClient client = HttpClient.create(server.URL)

    void "a GET-scoped filter guards the HEAD route of a supplier function"() {
        given:
        SecretFunction.CALLS.set(0)

        when:
        HttpStatus get = status(HttpRequest.GET('/head-secret'))
        HttpStatus head = status(HttpRequest.HEAD('/head-secret'))

        then:
        get == HttpStatus.FORBIDDEN
        head == HttpStatus.FORBIDDEN
        SecretFunction.CALLS.get() == 0
    }

    private HttpStatus status(HttpRequest<?> request) {
        try {
            return client.toBlocking().exchange(request).status()
        } catch (HttpClientResponseException e) {
            return e.status
        }
    }

    @Requires(property = 'spec.name', value = 'HeadFunctionFilterSpec')
    @FunctionBean("head-secret")
    static class SecretFunction implements Supplier<String> {
        static final AtomicInteger CALLS = new AtomicInteger()

        @Override
        String get() {
            CALLS.incrementAndGet()
            return 'secret-value'
        }
    }

    @Requires(property = 'spec.name', value = 'HeadFunctionFilterSpec')
    @ServerFilter(value = '/head-secret/**', methods = HttpMethod.GET)
    static class GetOnlyFilter {
        @RequestFilter
        HttpResponse<?> deny() {
            return HttpResponse.status(HttpStatus.FORBIDDEN)
        }
    }
}
