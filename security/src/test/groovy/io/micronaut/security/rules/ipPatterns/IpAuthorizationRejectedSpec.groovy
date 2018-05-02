package io.micronaut.security.rules.ipPatterns

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

class IpAuthorizationRejectedSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.authentication': true,
            'micronaut.security.enabled': true,
            'micronaut.security.ipPatterns': ['10.10.0.48']
    ], "test")

    @Shared
    @AutoCleanup
    RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    private HttpResponse get(String path) {
        HttpRequest req = HttpRequest.GET(path)
        client.toBlocking().exchange(req, String)
    }

    void "test accessing a non sensitive endpoint without authentication"() {
        when:
        get("/nonSensitive")

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status == HttpStatus.UNAUTHORIZED
    }
}
