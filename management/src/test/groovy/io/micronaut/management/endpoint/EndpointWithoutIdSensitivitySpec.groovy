package io.micronaut.management.endpoint

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.management.endpoint.annotation.Endpoint
import io.micronaut.management.endpoint.annotation.Read
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.web.router.Router
import spock.lang.Specification

class EndpointWithoutIdSensitivitySpec extends Specification {

    void "an id-less endpoint is recorded as sensitive and refused without authentication"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'EndpointWithoutIdSensitivitySpec'])
        def ctx = server.applicationContext
        def processor = ctx.getBean(EndpointSensitivityProcessor)
        def method = ctx.findExecutableMethod(NoIdEndpoint, "index").get()
        String path = ctx.getBean(Router).uriRoutes()
                .filter { it.targetMethod.declaringType == NoIdEndpoint }
                .map { it.uriMatchTemplate.toString() }
                .findFirst().get()
        def client = ctx.createBean(HttpClient, server.URL)

        when:
        HttpStatus status
        try {
            status = client.toBlocking().exchange(HttpRequest.GET(path), String).status()
        } catch (HttpClientResponseException e) {
            status = e.status
        }

        then:
        processor.getEndpointMethods().get(method) == Boolean.TRUE
        status == HttpStatus.UNAUTHORIZED

        cleanup:
        client?.close()
        server.close()
    }

    @Requires(property = 'spec.name', value = 'EndpointWithoutIdSensitivitySpec')
    @Endpoint
    static class NoIdEndpoint {
        @Read
        String index() { 'secret' }
    }
}
