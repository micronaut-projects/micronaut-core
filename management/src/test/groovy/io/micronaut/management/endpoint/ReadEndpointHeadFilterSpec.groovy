package io.micronaut.management.endpoint

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.management.endpoint.annotation.Endpoint
import io.micronaut.management.endpoint.annotation.Read
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicInteger

/**
 * The HEAD route of a management {@code @Read} operation runs the operation like GET does, so
 * GET-scoped filters apply to it.
 */
class ReadEndpointHeadFilterSpec extends Specification {

    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'ReadEndpointHeadFilterSpec'])

    @AutoCleanup
    HttpClient client = HttpClient.create(server.URL)

    void "a GET-scoped filter guards the HEAD route of a read operation"() {
        given:
        SecretEndpoint.CALLS.set(0)

        when:
        HttpStatus get = status(HttpRequest.GET('/head-secret-endpoint'))
        HttpStatus head = status(HttpRequest.HEAD('/head-secret-endpoint'))

        then:
        get == HttpStatus.FORBIDDEN
        head == HttpStatus.FORBIDDEN
        SecretEndpoint.CALLS.get() == 0
    }

    private HttpStatus status(HttpRequest<?> request) {
        try {
            return client.toBlocking().exchange(request).status()
        } catch (HttpClientResponseException e) {
            return e.status
        }
    }

    @Requires(property = 'spec.name', value = 'ReadEndpointHeadFilterSpec')
    @Endpoint(id = 'head-secret-endpoint', defaultSensitive = false)
    static class SecretEndpoint {
        static final AtomicInteger CALLS = new AtomicInteger()

        @Read
        String read() {
            CALLS.incrementAndGet()
            return 'secret-value'
        }
    }

    @Requires(property = 'spec.name', value = 'ReadEndpointHeadFilterSpec')
    @ServerFilter(value = '/head-secret-endpoint/**', methods = HttpMethod.GET)
    static class GetOnlyFilter {
        @RequestFilter
        HttpResponse<?> deny() {
            return HttpResponse.status(HttpStatus.FORBIDDEN)
        }
    }
}
