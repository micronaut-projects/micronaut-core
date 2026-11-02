package io.micronaut.management.endpoint

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter
import io.micronaut.management.endpoint.annotation.Endpoint
import io.micronaut.management.endpoint.annotation.Read
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

import java.net.http.HttpClient
import java.util.concurrent.atomic.AtomicInteger

/**
 * The HEAD route of a management {@code @Read} operation runs the operation like GET does, so
 * GET-scoped filters apply to it.
 */
class ReadEndpointHeadFilterSpec extends Specification {

    void "a GET-scoped filter guards the HEAD route of a read operation"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'ReadEndpointHeadFilterSpec'])
        HttpClient client = HttpClient.newHttpClient()
        def uri = URI.create("http://localhost:${server.port}/head-secret-endpoint")
        SecretEndpoint.CALLS.set(0)

        when:
        def get = client.send(java.net.http.HttpRequest.newBuilder(uri).build(), java.net.http.HttpResponse.BodyHandlers.discarding())
        def head = client.send(java.net.http.HttpRequest.newBuilder(uri).method('HEAD', java.net.http.HttpRequest.BodyPublishers.noBody()).build(),
                java.net.http.HttpResponse.BodyHandlers.discarding())

        then:
        get.statusCode() == 403
        head.statusCode() == 403
        SecretEndpoint.CALLS.get() == 0

        cleanup:
        client?.close()
        server?.close()
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
