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

class AuthorizationSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'authorization',
            'endpoints.health.enabled': true,
            'endpoints.health.sensitive': true,
            'micronaut.security.enabled': true,
            'micronaut.security.endpoints.login.enabled': true,
            'micronaut.security.interceptUrlMap': [
                    [pattern: '/urlMap/admin', access: ['ROLE_ADMIN', 'ROLE_X']],
                    [pattern: '/urlMap/**',    access: 'isAuthenticated()'],
                    [pattern: '/anonymous/**', access: 'isAnonymous()'],
            ]
    ], "test")
    @Shared @AutoCleanup RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    void "test /health is secured"() {
        when:
        client.toBlocking().exchange(HttpRequest.GET("/health"))

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status == HttpStatus.UNAUTHORIZED
    }

    void "test accessing an anonymous without authentication"() {
        when:
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET("/anonymous/hello"), String)

        then:
        response.body() == 'You are anonymous'
    }

    void "java.security.Principal Argument Binders binds even if Optional<Principal>"() {
        expect:
        embeddedServer.applicationContext.getBean(PrincipalArgumentBinder.class)

        when:
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET("/anonymous/hello")
                .basicAuth("valid", "password"), String)

        then:
        response.body() == 'You are valid'
    }

    void "test accessing the url map controller without authentication"() {
        when:
        client.toBlocking().exchange(HttpRequest.GET("/urlMap/authenticated"))

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status == HttpStatus.UNAUTHORIZED
    }

    void "test accessing the url map controller"() {
        when:
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET("/urlMap/authenticated")
                .basicAuth("valid", "password"), String)
        then:
        response.body() == "valid is authenticated"
    }

    void "test accessing the url map controller and bind to java.util.Principal"() {
        when:
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET("/urlMap/principal")
                .basicAuth("valid", "password"), String)

        then:
        response.body() == "valid is authenticated"
    }

    void "test accessing the url map admin action without the required role"() {
        when:
        client.toBlocking().exchange(HttpRequest.GET("/urlMap/admin")
                .basicAuth("valid", "password"), String)


        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status == HttpStatus.FORBIDDEN
    }

    void "test accessing the url map admin action with the required role"() {
        when:
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET("/urlMap/admin")
                .basicAuth("admin", "password"), String)

        then:
        response.body() == "You have admin"
    }

    void "test accessing the secured controller without authentication"() {when:
        when:
        client.toBlocking().exchange(HttpRequest.GET("/secured/authenticated"))

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status == HttpStatus.UNAUTHORIZED
    }

    void "test accessing the secured controller"() {
        when:
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET("/secured/authenticated")
                .basicAuth("valid", "password"), String)

        then:
        response.body() == "valid is authenticated"
    }

    void "test accessing the secured admin action without the required role"() {
        when:
        client.toBlocking().exchange(HttpRequest.GET("/secured/admin")
                .basicAuth("valid", "password"), String)

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status == HttpStatus.FORBIDDEN
    }

    void "test accessing the secured admin action with the required role"() {
        when:
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET("/secured/admin")
                .basicAuth("admin", "password"), String)

        then:
        response.body() == "You have admin"
    }

    void "test accessing a controller without a rule"() {
        when:
        client.toBlocking().exchange(HttpRequest.GET("/noRule/index")
                .basicAuth("valid", "password"), String)

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status == HttpStatus.FORBIDDEN
    }

    void "test accessing a non sensitive endpoint without authentication"() {
        when:
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET("/nonSensitive"), String)

        then:
        response.body() == "Not logged in"
    }

    void "test accessing a non sensitive endpoint with authentication"() {
        when:
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET("/nonSensitive")
                .basicAuth("valid", "password"), String)

        then:
        response.body() == "Logged in as valid"
    }

    void "test accessing a sensitive endpoint without authentication"() {
        when:
        client.toBlocking().exchange(HttpRequest.GET("/sensitive"), String)

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status == HttpStatus.UNAUTHORIZED
    }

    void "test accessing a sensitive endpoint with authentication"() {
        when:
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET("/sensitive")
                .basicAuth("valid", "password"), String)
        then:
        response.body() == "Hello valid"
    }

    void "test accessing a sensitive endpoint with Authentication binded with authentication"() {
        when:
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET("/sensitiveauthentication")
                .basicAuth("valid", "password"), String)
        then:
        response.body() == "Hello valid"
    }
}
