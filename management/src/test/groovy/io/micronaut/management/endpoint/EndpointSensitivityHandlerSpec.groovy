package io.micronaut.management.endpoint

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.management.endpoint.env.EnvironmentEndpoint
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.management.endpoint.EndpointSensitivityHandler
import jakarta.inject.Singleton
import spock.lang.Specification

class EndpointSensitivityHandlerSpec extends Specification {
    void "when a bean of type EndpointSensitivityHandler is present then the EndpointsFilter id disabled"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec.name': 'EndpointSensitivityHandlerSpec', 'endpoints.env.enabled': true], Environment.TEST)
        HttpClient httpClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())
        BlockingHttpClient client = httpClient.toBlocking()

        when:
        client.exchange("/${EnvironmentEndpoint.NAME}")

        then:
        noExceptionThrown()

        cleanup:
        client.close()
        httpClient.close()
        embeddedServer.close()
    }

    @Requires(property = 'spec.name', value = 'EndpointSensitivityHandlerSpec')
    @Singleton
    static class EndpointSensitivityHandlerImpl implements io.micronaut.management.endpoint.EndpointSensitivityHandler {

    }

}
