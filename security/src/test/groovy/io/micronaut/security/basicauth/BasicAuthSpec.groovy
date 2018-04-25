package io.micronaut.security.basicauth

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.authorization.AuthorizationUtils
import spock.lang.AutoCleanup
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

@Ignore
class BasicAuthSpec extends Specification implements AuthorizationUtils {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'basicauth',
            'endpoints.health.enabled': true,
            'endpoints.health.sensitive': true,
            "micronaut.security.enabled": true,
            "micronaut.security.basicAuth.enabled": true,
    ], "test")

    @Shared @AutoCleanup
    RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    void "test /health is not accesible if you don't supply Basic Auth in HTTP Header Authorization"() {
        expect:
        when:
        get("/health")

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status == HttpStatus.UNAUTHORIZED
    }

    void "test /health is not accesible if you don't supply a valid Base64 encoded token in the Basic Auth in HTTP Header Authorization"() {
        when:
        get("/health", 'bogus', 'Basic')

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status == HttpStatus.UNAUTHORIZED
    }

    void "test /health is secured but accesible if you supply valid credentials with Basic Auth"() {
        when:
        String token = 'dXNlcjpwYXNzd29yZA==' // user:passsword Base64
        get("/health", token, 'Basic')

        then:
        noExceptionThrown()
    }

    void "test /health is not accesible if you valid Base64 encoded token but authentication fails"() {
        when:
        String token = 'dXNlcjp1c2Vy' // user:user Base64 encoded
        get("/health", token, 'Basic')

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status == HttpStatus.UNAUTHORIZED
    }
}
