package io.micronaut.security.rules.ipPatterns

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class IpAuthorizationApprovedSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'ipPatterns',
            'micronaut.security.enabled': true,
            'micronaut.security.ipPatterns': ['10.10.0.48']
    ], "test")

    @Shared
    @AutoCleanup
    RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    void "even if you are in the correct ip range, accessing the secured controller without authentication should return unauthorized"() {
        expect:
        embeddedServer.applicationContext.getBean(CustomAuthenticationProvider.class)

        when:
        HttpRequest req = HttpRequest.GET("/secured/authenticated")
                .basicAuth("user", "password")
        client.toBlocking().exchange(req, String)

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status == HttpStatus.FORBIDDEN
    }
}
