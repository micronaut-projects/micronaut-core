package io.micronaut.security.authorization

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.authentication.UsernamePasswordCredentials
import io.micronaut.security.token.render.BearerAccessRefreshToken
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class AuthorizationSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.authentication': true,
            "micronaut.security.enabled": true,
            "micronaut.security.endpoints.login": true,
            "micronaut.security.token.signature.secret": 'qrD6h8K6S9503Q06Y6Rfk21TErImPYqa',
            "micronaut.security.interceptUrlMap": [
                    [pattern: "/urlMap/admin", access: "ROLE_ADMIN"],
                    [pattern: "/urlMap/**",    access: "isAuthenticated()"],
            ]
    ], "test")
    @Shared @AutoCleanup RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    String loginWith(String username = "valid") {
        def creds = new UsernamePasswordCredentials(username, "valid")
        def resp = client.toBlocking().exchange(HttpRequest.POST('/login', creds), BearerAccessRefreshToken)
        resp.body().accessToken
    }

    void "test accessing the url map controller without authentication"() {
        when:
        client.toBlocking()
                .exchange(HttpRequest.GET("/urlMap/authenticated"), String)

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status == HttpStatus.UNAUTHORIZED
    }

    void "test accessing the url map controller"() {
        given:
        String token = loginWith("valid")

        when:
        HttpResponse<String> response = client.toBlocking()
                .exchange(HttpRequest.GET("/urlMap/authenticated")
                    .header("Authorization", "Bearer ${token}".toString()), String)

        then:
        response.body() == "You are authenticated"
    }

    void "test accessing the url map admin action without the required role"() {
        given:
        String token = loginWith("valid")

        when:
        HttpResponse<String> response = client.toBlocking()
                .exchange(HttpRequest.GET("/urlMap/admin")
                .header("Authorization", "Bearer ${token}".toString()), String)

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status == HttpStatus.UNAUTHORIZED
    }

    void "test accessing the url map admin action with the required role"() {
        given:
        String token = loginWith("admin")

        when:
        HttpResponse<String> response = client.toBlocking()
                .exchange(HttpRequest.GET("/urlMap/admin")
                .header("Authorization", "Bearer ${token}".toString()), String)

        then:
        response.body() == "You have admin"
    }

    void "test accessing the secured controller without authentication"() {
        when:
        client.toBlocking()
                .exchange(HttpRequest.GET("/secured/authenticated"), String)

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status == HttpStatus.UNAUTHORIZED
    }

    void "test accessing the secured controller"() {
        given:
        String token = loginWith("valid")

        when:
        HttpResponse<String> response = client.toBlocking()
                .exchange(HttpRequest.GET("/secured/authenticated")
                .header("Authorization", "Bearer ${token}".toString()), String)

        then:
        response.body() == "You are authenticated"
    }

    void "test accessing the secured admin action without the required role"() {
        given:
        String token = loginWith("valid")

        when:
        HttpResponse<String> response = client.toBlocking()
                .exchange(HttpRequest.GET("/secured/admin")
                .header("Authorization", "Bearer ${token}".toString()), String)

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status == HttpStatus.UNAUTHORIZED
    }

    void "test accessing the secured admin action with the required role"() {
        given:
        String token = loginWith("admin")

        when:
        HttpResponse<String> response = client.toBlocking()
                .exchange(HttpRequest.GET("/secured/admin")
                .header("Authorization", "Bearer ${token}".toString()), String)

        then:
        response.body() == "You have admin"
    }

    void "test accessing a controller without a rule"() {
        given:
        String token = loginWith("valid")

        when:
        HttpResponse<String> response = client.toBlocking()
                .exchange(HttpRequest.GET("/noRule/index")
                .header("Authorization", "Bearer ${token}".toString()), String)

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status == HttpStatus.UNAUTHORIZED
    }
}
