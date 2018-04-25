package io.micronaut.security.authentication.providers

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.authentication.UsernamePasswordCredentials
import io.micronaut.security.token.render.BearerAccessRefreshToken
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class DelegatingAuthenticationProviderSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.authentication': true,
            "micronaut.security.enabled": true,
            "micronaut.security.endpoints.login": true,
            "micronaut.security.jwt.enabled": true,
            "micronaut.security.jwt.bearer.enabled": true,
            "micronaut.security.jwt.generator.signature.enabled": true,
            "micronaut.security.jwt.generator.signature.secret": 'qrD6h8K6S9503Q06Y6Rfk21TErImPYqa'
    ], "test")
    @Shared @AutoCleanup RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    @Unroll
    void "test invalid authentication with username #username"() {
        when:
        def creds = new UsernamePasswordCredentials(username, password)
        def resp = client.toBlocking().exchange(HttpRequest.POST('/login', creds), BearerAccessRefreshToken)

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status == HttpStatus.UNAUTHORIZED
        e.message == message

        where:
        username          | password  | message
        "notFound"        | "valid"   | "User Not Found"
        "valid"           | "invalid" | "Credentials Do Not Match"
        "disabled"        | "valid"   | "User Disabled"
        "accountExpired"  | "valid"   | "Account Expired"
        "passwordExpired" | "valid"   | "Password Expired"
        "accountLocked"   | "valid"   | "Account Locked"
    }

    void "test valid authentication"() {
        when:
        def creds = new UsernamePasswordCredentials("valid", "valid")
        def resp = client.toBlocking().exchange(HttpRequest.POST('/login', creds), BearerAccessRefreshToken)

        then:
        resp.status == HttpStatus.OK
        resp.body().accessToken
        resp.body().refreshToken
        resp.body().username == "valid"
        resp.body().roles == ["foo", "bar"]
        resp.body().expiresIn
    }

}
