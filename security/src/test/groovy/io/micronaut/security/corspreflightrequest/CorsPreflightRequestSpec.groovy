package io.micronaut.security.corspreflightrequest

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class CorsPreflightRequestSpec extends Specification {

    @AutoCleanup
    @Shared
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'corspreflightrequest',
            'micronaut.server.cors.enabled': true,
            'micronaut.security.enabled': true,
    ], Environment.TEST)

    @AutoCleanup
    @Shared
    RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    def "preflight requests are authorized"() {
        given:
        HttpRequest request = HttpRequest.OPTIONS("/register")
                .header("Access-Control-Request-Method", "DELETE")
                .header("Access-Control-Request-Headers", "origin, x-requested-with")
                .header("Origin", "https://foo.bar.org")

        when:
        HttpResponse rsp = rxClient.toBlocking().exchange(request)

        then:
        noExceptionThrown()
        rsp.status() == HttpStatus.OK
    }
}
