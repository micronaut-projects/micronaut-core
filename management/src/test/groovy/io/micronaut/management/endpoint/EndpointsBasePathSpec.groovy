package io.micronaut.management.endpoint

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class EndpointsBasePathSpec extends Specification {

    @AutoCleanup
    @Shared
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer,
            [
                    'spec.name': getClass().simpleName,
                    'endpoints.all.path': '/admin',
                    'endpoints.all.enabled': true
            ], "test")

    @AutoCleanup
    @Shared
    RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    def "due to the change of endpoints base path to /admin, Health endpoint is available at /admin/health"() {
        when:
        rxClient.toBlocking().retrieve('/admin/health')

        then:
        noExceptionThrown()
    }

    def "due to the change of endpoints base path to /admin, Health endpoint is not available at /health"() {
        when:
        rxClient.toBlocking().retrieve('/health')

        then:
        def e = thrown(HttpClientResponseException)

        when:
        def response = e.response

        then:
        response.status == HttpStatus.NOT_FOUND
    }
}
