package io.micronaut.security.rules.sensitive

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class EndpointWithPrincipalAndSensitivityOverrideSpec extends Specification {
    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'sensitive',
            'micronaut.security.enabled': true,
            'endpoints.defaultendpoint.sensitive': false,

    ], "test")

    @Shared
    @AutoCleanup
    RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    private HttpResponse get(String path) {
        HttpRequest req = HttpRequest.GET(path)
        client.toBlocking().exchange(req, String)
    }

    void "if endpoint sensitive is set to false via configuration property, it overrides default sensitive true"() {
        when:
        HttpResponse<String> response = get("/defaultendpoint")

        then:
        response.body() == "Not logged in"
    }
}
