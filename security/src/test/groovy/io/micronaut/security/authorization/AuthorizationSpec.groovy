package io.micronaut.security.authorization

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.authentication.PrincipalArgumentBinder
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class AuthorizationSpec extends Specification implements AuthorizationUtils {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.authentication': true,
            'endpoints.health.enabled': true,
            'endpoints.health.sensitive': true,
            "micronaut.security.enabled": true,
            "micronaut.security.endpoints.login": true,
            "micronaut.security.token.bearer.enabled": true,
            "micronaut.security.jwt.enabled": true,
            "micronaut.security.jwt.generator.signature.enabled": true,
            "micronaut.security.jwt.generator.signature.secret": 'qrD6h8K6S9503Q06Y6Rfk21TErImPYqa',
            "micronaut.security.interceptUrlMap": [
                    [pattern: "/urlMap/admin", access: ["ROLE_ADMIN", "ROLE_X"]],
                    [pattern: "/urlMap/**",    access: "isAuthenticated()"],
                    [pattern: "/anonymous/**", access: "isAnonymous()"],
            ]
    ], "test")
    @Shared @AutoCleanup RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    void "test /health is secured"() {
        when:
        get("/health")

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status == HttpStatus.UNAUTHORIZED
    }

    void "test accessing an anonymous without authentication"() {
        when:
        HttpResponse<String> response = get("/anonymous/hello")

        then:
        response.body() == 'You are anonymous'
    }

    void "java.security.Principal Argument Binders binds even if Optional<Principal>"() {
        expect:
        embeddedServer.applicationContext.getBean(PrincipalArgumentBinder.class)

        when:
        String token = loginWith("valid")

        then:
        token

        when:
        HttpResponse<String> response = get("/anonymous/hello", token)

        then:
        response.body() == 'You are valid'
    }

    void "test accessing the url map controller without authentication"() {
        when:
        get("/urlMap/authenticated")

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status == HttpStatus.UNAUTHORIZED
    }

    void "test accessing the url map controller"() {
        when:
        String token = loginWith("valid")

        then:
        token

        when:
        HttpResponse<String> response = get("/urlMap/authenticated", token)

        then:
        response.body() == "valid is authenticated"
    }

    void "test accessing the url map controller and bind to java.util.Principal"() {
        when:
        String token = loginWith("valid")

        then:
        token

        when:
        HttpResponse<String> response = get("/urlMap/principal", token)

        then:
        response.body() == "valid is authenticated"
    }

    void "test accessing the url map admin action without the required role"() {
        given:
        String token = loginWith("valid")

        when:
        get("/urlMap/admin", token)

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status == HttpStatus.FORBIDDEN
    }

    void "test accessing the url map admin action with the required role"() {
        given:
        String token = loginWith("admin")

        when:
        HttpResponse<String> response = get("/urlMap/admin", token)

        then:
        response.body() == "You have admin"
    }

    void "test accessing the secured controller without authentication"() {
        when:
        get("/secured/authenticated")

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status == HttpStatus.UNAUTHORIZED
    }

    void "test accessing the secured controller"() {
        given:
        String token = loginWith("valid")

        when:
        HttpResponse<String> response = get("/secured/authenticated", token)

        then:
        response.body() == "valid is authenticated"
    }

    void "test accessing the secured admin action without the required role"() {
        given:
        String token = loginWith("valid")

        when:
        get("/secured/admin", token)

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status == HttpStatus.FORBIDDEN
    }

    void "test accessing the secured admin action with the required role"() {
        given:
        String token = loginWith("admin")

        when:
        HttpResponse<String> response = get("/secured/admin", token)

        then:
        response.body() == "You have admin"
    }

    void "test accessing a controller without a rule"() {
        given:
        String token = loginWith("valid")

        when:
        get("/noRule/index", token)

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status == HttpStatus.FORBIDDEN
    }

    void "test accessing a non sensitive endpoint without authentication"() {
        when:
        HttpResponse<String> response = get("/nonSensitive")

        then:
        response.body() == "Not logged in"
    }

    void "test accessing a non sensitive endpoint with authentication"() {
        given:
        String token = loginWith("valid")

        when:
        HttpResponse<String> response = get("/nonSensitive", token)

        then:
        response.body() == "Logged in as valid"
    }

    void "test accessing a sensitive endpoint without authentication"() {
        when:
        get("/sensitive")

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status == HttpStatus.UNAUTHORIZED
    }

    void "test accessing a sensitive endpoint with authentication"() {
        given:
        String token = loginWith("valid")

        when:
        HttpResponse<String> response = get("/sensitive", token)

        then:
        response.body() == "Hello valid"
    }
}
