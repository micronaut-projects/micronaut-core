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
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

/**
 * The HEAD route of a {@code @Read} endpoint operation is the implicit HEAD of its GET route: the
 * filters scoped to GET apply to it.
 */
class ReadEndpointHeadFilterSpec extends Specification {

    void "a filter scoped to GET applies to HEAD of a read endpoint"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'ReadEndpointHeadFilterSpec', 'endpoints.health.sensitive': false])
        HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

        when:
        client.toBlocking().exchange(HttpRequest.HEAD('/health'))

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.UNAUTHORIZED

        cleanup:
        client.close()
        server.close()
    }

    @Requires(property = 'spec.name', value = 'ReadEndpointHeadFilterSpec')
    @ServerFilter(value = '/health', methods = HttpMethod.GET)
    static class GetOnlyFilter {
        @RequestFilter
        HttpResponse<?> deny() {
            return HttpResponse.unauthorized()
        }
    }
}
