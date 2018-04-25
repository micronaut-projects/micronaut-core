package io.micronaut.security.authorization

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class IpAuthorizationApprovedSpec extends Specification implements AuthorizationUtils {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.authentication': true,
            "micronaut.security.enabled": true,
            "micronaut.security.endpoints.login": true,
            "micronaut.security.jwt.enabled": true,
            "micronaut.security.jwt.generator.signature.enabled": true,
            "micronaut.security.jwt.generator.signature.secret": 'qrD6h8K6S9503Q06Y6Rfk21TErImPYqa',
            "micronaut.security.ipPatterns": ['10.10.0.48', '127.0.0.*']
    ], "test")

    @Shared @AutoCleanup RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    void "test accessing a non sensitive endpoint without authentication"() {
        when:
        HttpResponse<String> response = get("/nonSensitive")

        then:
        response.body() == "World"
    }

    void "even if you are in the correct ip range, accessing the secured controller without authentication should return unauthorized"() {
        when:
        get("/secured/authenticated")

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status == HttpStatus.UNAUTHORIZED
    }
}
