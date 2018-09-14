package io.micronaut.security.endpoints

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.authentication.UsernamePasswordCredentials
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class LoginControllerValidationSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'loginpathconfigurable',
            'micronaut.security.enabled': true,
            'micronaut.security.endpoints.login.enabled': true,
    ], Environment.TEST)

    @Shared
    @AutoCleanup
    RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    @Unroll("{\"username\": \"#username\", \"password\": \"#password\"} is invalid payload")
    void "LoginController responds BAD_REQUEST if POJO sent to /login is invalid"(String username, String password) {
        given:
        UsernamePasswordCredentials creds = new UsernamePasswordCredentials(username, password)

        when:
        client.toBlocking().exchange(HttpRequest.POST('/login', creds))

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status == HttpStatus.BAD_REQUEST

        where:
        username | password
        null     | 'aabbc12345678'
        ''       | 'aabbc12345678'
        'johnny' | null
        'johnny' | ''
    }
}
