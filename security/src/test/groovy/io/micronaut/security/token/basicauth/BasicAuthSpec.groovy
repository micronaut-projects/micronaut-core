package io.micronaut.security.token.basicauth

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class BasicAuthSpec extends Specification  {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'basicauth',
            'endpoints.beans.enabled': true,
            'endpoints.beans.sensitive': true,
            'micronaut.security.enabled': true,
    ], 'test')

    @Shared
    @AutoCleanup
    RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    void "test /beans is not accesible if you don't supply Basic Auth in HTTP Header Authorization"() {
        expect:
        embeddedServer.applicationContext.getBean(BasicAuthTokenReader.class)
        embeddedServer.applicationContext.getBean(BasicAuthTokenValidator.class)
        embeddedServer.applicationContext.getBean(AuthenticationProviderUserPassword.class)

        when:
        String path = "/beans"
        client.toBlocking().exchange(HttpRequest.GET(path), String)

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status == HttpStatus.UNAUTHORIZED
    }

    void "test /beans is not accesible if you don't supply a valid Base64 encoded token in the Basic Auth in HTTP Header Authorization"() {
        expect:
        embeddedServer.applicationContext.getBean(BasicAuthTokenReader.class)
        embeddedServer.applicationContext.getBean(BasicAuthTokenValidator.class)
        embeddedServer.applicationContext.getBean(AuthenticationProviderUserPassword.class)

        when:
        String token = 'Basic'
        String path = "/beans"
        client.toBlocking().exchange(HttpRequest.GET(path).header("Authorization", "Basic ${token}".toString()), String)

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status == HttpStatus.UNAUTHORIZED
    }

    void "test /beans is secured but accesible if you supply valid credentials with Basic Auth"() {
        expect:
        embeddedServer.applicationContext.getBean(BasicAuthTokenReader.class)
        embeddedServer.applicationContext.getBean(BasicAuthTokenValidator.class)
        embeddedServer.applicationContext.getBean(AuthenticationProviderUserPassword.class)

        when:
        String token = 'dXNlcjpwYXNzd29yZA==' // user:passsword Base64
        String path = "/beans"
        client.toBlocking().exchange(HttpRequest.GET(path).header("Authorization", "Basic ${token}".toString()), String)

        then:
        noExceptionThrown()
    }

    void "test /beans is not accesible if you valid Base64 encoded token but authentication fails"() {
        expect:
        embeddedServer.applicationContext.getBean(BasicAuthTokenReader.class)
        embeddedServer.applicationContext.getBean(BasicAuthTokenValidator.class)
        embeddedServer.applicationContext.getBean(AuthenticationProviderUserPassword.class)

        when:
        String token = 'dXNlcjp1c2Vy' // user:user Base64 encoded
        String path = "/beans"
        client.toBlocking().exchange(HttpRequest.GET(path).header("Authorization", "Basic ${token}".toString()), String)

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status == HttpStatus.UNAUTHORIZED
    }
}
